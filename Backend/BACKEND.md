# Praxis Service — Backend

The Spring Boot application behind [Praxis](../../README.md): authentication, source ingestion, static analysis, the LLM review layer, health scoring, and the async pipeline that ties them together. It ships as **one deployable JAR** built as a **Spring Modulith** (modular monolith) — module boundaries are real and enforced by a build-time test.

> New to the project? Read the [root README](../../README.md) first for the big picture and the one-command Docker setup, then come back here for backend specifics.

---

## Tech stack

| Concern | Choice | Notes |
|---|---|---|
| Language / runtime | **Java 21** | Virtual threads carry the I/O-heavy pipeline workers |
| Framework | **Spring Boot 4.1** | Web (MVC + SSE), Security, Validation, Actuator |
| Modularity | **Spring Modulith 2.1** | Enforced package boundaries; `ModularityTests` fails the build on a violation |
| Persistence | **Spring Data JPA + PostgreSQL 16** (`pgvector` image) | One store for relational data + future embeddings |
| Migrations | **Flyway** | Owns the schema; Hibernate is `ddl-auto: validate` (never creates tables) |
| Async | **Redis 7** — Streams (job queue) + Pub/Sub (progress) | At-least-once jobs; cross-JVM progress fan-out |
| Auth | **Spring Security + JJWT** | Stateless HS256 JWT, BCrypt passwords |
| AST | **JavaParser** (+ symbol-solver) | Deterministic metrics & pattern detection, no AI |
| LLM | plain Spring **`RestClient`** per provider | Ollama / OpenAI / Gemini, or an offline stub — chosen by config, no Spring AI dependency |
| Repo fetch | **JGit** | Shallow clone into a sandboxed workspace |
| Build | **Gradle** (wrapper) | `./gradlew` — toolchain pins JDK 21 |

---

## Module architecture

Everything lives under `src/main/java/com/praxis/`. Each module is a package with `api/` (the only surface other modules may call), `internal/` (private impl), and where relevant `web/`, `domain/`, `config/`. The **golden rule**: a module may only reach another through its `api` package — enforced by `ModularityTests` (ArchUnit under the hood), so a cross-boundary import fails `./gradlew test`.

```
com/praxis/
├── identity/     # auth & tenancy — JWT, BCrypt; exports IdentityFacade.currentTenantId()
├── intake/       # fetch source into a sandboxed workspace; stage uploads
├── prism/        # static analysis: AST → metrics → patterns → risk score; owns findings store
├── conductor/    # orchestration: REST, Redis worker, state machine, the risk funnel, SSE
├── cortex/       # LLM layer: one provider chosen at startup; writes AI findings + llm_call rows
├── verdict/      # health score (versioned pure formula)
├── chronicle/    # dashboard read-side (owns no data; aggregates conductor + prism)
├── recall/       # 🔜 RAG over pgvector (Phase 2 placeholder)
├── ledger/       # 🔜 cost governance / budgets (Phase 2 placeholder)
└── common/       # open shared kernel: ApiError, ApiException, GlobalExceptionHandler, SourceType
```

| Module | Key classes | Exposes to other modules |
|---|---|---|
| `identity` | `AuthService`, `JwtService`, `JwtAuthFilter`, `SecurityConfig` | `IdentityFacade` (current tenant/user/role), `PraxisPrincipal` |
| `intake` | `SourceFetcherImpl`, `GitRepositoryCloner`, `ZipExtractor`, `JavaFileScanner`, `UploadStoreImpl`, `UploadJanitor` | `SourceFetcher`, `UploadStore`, DTOs (`FetchCommand/Result`, `JavaFile`, `StagedSource`) |
| `prism` | `AstParser`, `MetricCalculator`, `PatternDetector`, `RiskScorer`, `StaticAnalyzerImpl` | `StaticAnalyzer`, `FindingWriter`, `AnalysisResultQuery` |
| `conductor` | `AnalysisOrchestrator`, `AnalysisPipeline`, `AnalysisWorker`, `SseEmitterRegistry`, `JobPublisher`, `ProgressPublisher` | `AnalysisQuery`; REST via `AnalysisController` |
| `cortex` | `CortexService`, `AbstractHttpLlmClient`, `Ollama/OpenAi/Gemini/StubLlmClient` | `LlmEnricher` |
| `verdict` | `HealthScorerImpl` | `HealthScorer` |
| `chronicle` | `DashboardController` | — (REST only) |

Each implemented module also carries a `<module>.md` design doc next to its code (e.g. `conductor/conductor.md`).

---

## How the pipeline works

An analysis is a **long-running job**, never a request/response. `POST /analyses` returns `202` in milliseconds; a worker consumes the job from a Redis Stream and drives the state machine:

```
QUEUED → FETCHING → PARSING → ANALYZING → SUMMARIZING → SCORING → COMPLETE | FAILED
```

1. **`AnalysisOrchestrator.start`** (request thread) — resolves the tenant from the JWT, saves `repository` + `analysis(QUEUED)`, and enqueues `{analysisId, tenantId}` to the Redis Stream **only after the DB commit** (`TransactionSynchronization.afterCommit`), so a worker can never read a row that isn't there yet. Returns `202`.
2. **`AnalysisWorker`** consumes from the `praxis-workers` consumer group and calls **`AnalysisPipeline.run`**.
3. **`AnalysisPipeline`** walks the stages: FETCHING (`intake.SourceFetcher`) → PARSING (`prism.StaticAnalyzer`) → ANALYZING (the **funnel**: keep units with `riskScore ≥ praxis.analysis.risk-threshold`) → SUMMARIZING (`cortex.LlmEnricher` on the selected units only) → SCORING (`verdict.HealthScorer`). Progress is published to a Redis Pub/Sub channel after every stage.
4. **`SseEmitterRegistry`** relays those progress events to any subscribed browser over SSE — so the worker JVM and the web JVM holding the stream can even be different instances.

**Reliability invariants** (all unit-tested — see `AnalysisPipelineTest`):

- **Publish-after-commit** — job enqueued only post-commit.
- **Terminal-status idempotency** — the `Analysis` aggregate refuses transitions once COMPLETE/FAILED, so a redelivered Redis message is a no-op.
- **ACK-in-finally** — the worker acknowledges the stream message even on failure (already recorded on the row), so a poison message can't loop.
- **Workspace-release-in-finally** — fetched source is always deleted (incl. Windows read-only git pack files).
- **Graceful LLM degradation** — an unreachable/keyless provider stops the batch and the analysis **completes static-only**; an LLM outage never fails an analysis.

---

## Running locally

Requires **JDK 21**. Postgres and Redis must be up (run them from `Docker/` — see the [root setup guide](../../README.md#9-setup-guide-zero-to-running)).

```bash
# from Docker/: docker compose up -d postgres redis ollama

cd Backend/praxis-service

# pick an LLM provider (or omit for the offline stub)
# PowerShell:  $env:CORTEX_PROVIDER = "ollama"
# bash:        export CORTEX_PROVIDER=ollama

./gradlew bootRun
```

A healthy start logs **Flyway migrating to version 2**, `Conductor stream container started on group 'praxis-workers'`, and (with ollama) `Cortex using Ollama provider`. The API is on **http://localhost:8145**.

### Common Gradle tasks

| Command | What it does |
|---|---|
| `./gradlew bootRun` | Run the app against local Postgres/Redis |
| `./gradlew test` | Full test suite incl. the Modulith boundary check |
| `./gradlew bootJar` | Build the runnable fat jar into `build/libs/` |
| `./gradlew build` | Compile, test, and assemble |

### Docker

The [`Dockerfile`](./Dockerfile) is multi-stage (JDK build → non-root JRE-Alpine runtime, with a `/actuator/health` HEALTHCHECK). To run the backend in a container instead of `bootRun`: `docker compose --profile app up -d --build backend` (from `Docker/`) — it wires to the Postgres/Redis/Ollama services and exposes the same port 8145. See the [root setup guide](../../README.md#9-setup-guide-zero-to-running).

---

## Configuration

All settings live in `src/main/resources/application.yaml` and read from env vars with dev-friendly defaults. **The committed defaults (DB password, JWT secret) are for local dev only — always override them outside your machine.** In Docker these are injected via canonical Spring env vars (`SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`) which override the yaml through Spring's relaxed binding.

| Env var | Default | Meaning |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | localhost:5432 `praxis_db` | Postgres (or `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`) |
| `REDIS_HOST` / `REDIS_PORT` | localhost:6379 | Redis (or `SPRING_DATA_REDIS_HOST/PORT`) |
| `JWT_SECRET` | dev value | HS256 signing key — **≥ 32 bytes** |
| `CORTEX_PROVIDER` | `stub` | `stub` (offline) · `ollama` · `openai` · `gemini` |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | localhost:11434 / `qwen2.5-coder:7b` | local LLM |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` | — | cloud LLM keys |
| `INTAKE_WORKSPACE_ROOT` | `${tmp}/praxis-workspaces` | ephemeral fetch sandbox |

App-tuning keys under `praxis.*`: `analysis.risk-threshold` (60 — the funnel cutoff), `prism.*` (detector thresholds), `intake.*` (safety caps, `allowed-upload-extensions`, `upload-retention-hours`), `cortex.*` (per-provider blocks, `temperature`, `prompt-version`).

---

## Database

Flyway-managed under `src/main/resources/db/migration`: `V1__init.sql` creates all tables; `V2__analysis_results.sql` adds `file_result.source` + indexes. On a fresh volume Flyway runs automatically on boot.

```
tenant ──< app_user
tenant ──< repository ──< analysis ──< file_result ──< code_unit ──< issue_finding
                          analysis ──< llm_call            code_unit ── embedding (Phase 2)
```

Ownership: `tenant`/`app_user` → identity · `repository`/`analysis` → conductor · `file_result`/`code_unit`/`issue_finding` → prism (source text is stored so the dashboard outlives the deleted workspace) · `llm_call` → cortex (→ ledger later) · `embedding` → recall (Phase 2). Full column detail is in the [root README](../../README.md#7-database-schema).

---

## Testing

```bash
./gradlew test
```

- **Pure unit tests** (no Spring/DB/network) — the bulk: Prism metrics & detectors against tiny Java snippets, `RiskScorer` math, the Verdict formula, Cortex batching + degradation with a mocked client, Intake zip-slip/zip-bomb/workspace lifecycle, upload store & janitor.
- **`ModularityTests`** — Spring Modulith boundary verification; the build fails if any module touches another's internals. Run this after any refactor that moves code between modules.
- **`PraxisServiceApplicationTests`** — full context boot; needs the dockerized Postgres/Redis running.

Prism visitors and the scoring formulas are deterministic by design, which is what makes them cheap to unit-test without any AI in the loop.

---

## Conventions

- **Talk across modules only through `api`.** Need something from another module? Use its `api` type, or add one there — never import its `internal`. The build enforces this.
- **Tenant scoping is code, not convention.** Every read goes through `findByIdAndTenantId(...)`; the tenant comes from the JWT via `IdentityFacade`, never from a request body. Cross-tenant access returns **404, never 403** (existence isn't leaked).
- **Errors are one shape.** Throw an `ApiException` subclass (status + stable `code`); `GlobalExceptionHandler` renders it as `{code, message, traceId}`. Don't hand-roll error responses in controllers.
- **The queue carries IDs, the DB carries truth.** Jobs are `{analysisId, tenantId}`; workers reload state, so redelivery is always safe. Keep it that way — don't put mutable state on the stream.
- **Findings record provenance.** Every finding is `STATIC` or `AI`. Preserve that so the UI can present deterministic vs. generative results differently.
- **Adding an LLM provider** is one class implementing `LlmClient` (extend `AbstractHttpLlmClient`) + a `@ConditionalOnProperty` on `praxis.cortex.provider` + a config block — no changes to `CortexService` or any caller.
