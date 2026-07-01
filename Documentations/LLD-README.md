# Praxis — Low Level Design (LLD)

> **How to read this.** The HLD told you *what* and *why*. This LLD tells you *how* — exact packages, classes, database tables, API endpoints, and the step-by-step build/run flow. Build Phase-1 (MVP) modules in this order: **Identity → Intake → Prism → Conductor → Cortex → Verdict → Chronicle**, then add **Recall** and **Ledger**. The **Setup** section is at the very end.

---

## 1. Project layout (modular monolith)

One Spring Boot app, one Gradle build, packages split **by module** (not by layer). Each module has its own `api` (public, other modules may call), `domain`, `internal` (private), and where relevant `web`.

```
praxis/
├── backend/                       # Spring Boot app (one deployable jar)
│   ├── build.gradle
│   ├── src/main/java/com/praxis/
│   │   ├── PraxisApplication.java
│   │   ├── common/                # shared: errors, ids, base classes, config
│   │   ├── identity/              # Module 1
│   │   │   ├── api/               # IdentityFacade, DTOs
│   │   │   ├── domain/            # Tenant, User entities
│   │   │   ├── internal/          # services, repos
│   │   │   └── web/               # AuthController
│   │   ├── intake/                # Module 2
│   │   ├── prism/                 # Module 3
│   │   ├── conductor/             # Module 4 (orchestration + worker)
│   │   ├── cortex/                # Module 5 (LLM)
│   │   ├── recall/                # Module 6 (RAG)  -- Phase 2
│   │   ├── verdict/               # Module 7 (scoring)
│   │   ├── chronicle/             # Module 8 (reporting/API)
│   │   └── ledger/                # Module 9 (usage/cost)
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/          # Flyway SQL migrations V1__init.sql ...
├── frontend/                      # React app (Vite)
│   └── src/...
├── docker/
│   ├── docker-compose.yml         # full stack (used "later")
│   ├── backend.Dockerfile
│   └── frontend.Dockerfile
└── README.md
```

> **Rule:** a module may only call another module through its `api` package. Enforce this with Spring Modulith's verification test (`ApplicationModules.of(App.class).verify()`).

---

## 2. Domain model & database schema

PostgreSQL, managed by **Flyway** migrations. IDs are UUIDs. `pgvector` provides the `vector` type.

```sql
-- V1__init.sql (abridged; column types simplified for readability)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE tenant (
  id             UUID PRIMARY KEY,
  name           TEXT NOT NULL,
  code_residency TEXT NOT NULL DEFAULT 'CLOUD_ALLOWED',  -- or 'LOCAL_ONLY'
  plan           TEXT NOT NULL DEFAULT 'FREE',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL REFERENCES tenant(id),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'MEMBER',           -- MEMBER | ADMIN
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE repository (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenant(id),
  name        TEXT NOT NULL,
  source_type TEXT NOT NULL,                              -- GITHUB | ZIP
  source_ref  TEXT NOT NULL,                              -- url or stored file key
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE analysis (
  id             UUID PRIMARY KEY,
  repository_id  UUID NOT NULL REFERENCES repository(id),
  tenant_id      UUID NOT NULL REFERENCES tenant(id),
  status         TEXT NOT NULL,                           -- QUEUED..COMPLETE|FAILED
  health_score   INT,                                     -- 0..100, null until scored
  prompt_version TEXT NOT NULL,
  error_message  TEXT,
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE file_result (
  id          UUID PRIMARY KEY,
  analysis_id UUID NOT NULL REFERENCES analysis(id),
  path        TEXT NOT NULL,
  loc         INT,
  complexity  INT,
  class_count INT
);

CREATE TABLE code_unit (                                  -- a method or class
  id             UUID PRIMARY KEY,
  analysis_id    UUID NOT NULL REFERENCES analysis(id),
  file_result_id UUID NOT NULL REFERENCES file_result(id),
  unit_type      TEXT NOT NULL,                           -- CLASS | METHOD
  name           TEXT NOT NULL,
  start_line     INT,
  end_line       INT,
  source_hash    TEXT NOT NULL,                           -- sha256 of normalized source
  risk_score     INT                                      -- from Prism; drives the funnel
);

CREATE TABLE issue_finding (
  id            UUID PRIMARY KEY,
  code_unit_id  UUID REFERENCES code_unit(id),
  analysis_id   UUID NOT NULL REFERENCES analysis(id),
  type          TEXT NOT NULL,                            -- LONG_METHOD, GOD_OBJECT, ...
  severity      TEXT NOT NULL,                            -- INFO | MINOR | MAJOR | CRITICAL
  source        TEXT NOT NULL,                            -- STATIC | AI
  message       TEXT NOT NULL,
  suggestion    TEXT,                                     -- AI refactor text (nullable)
  start_line    INT,
  end_line      INT
);

CREATE TABLE embedding (                                  -- Recall (Phase 2)
  code_unit_id UUID PRIMARY KEY REFERENCES code_unit(id),
  vector       vector(768)
);

CREATE TABLE llm_call (                                   -- Ledger
  id          UUID PRIMARY KEY,
  analysis_id UUID REFERENCES analysis(id),
  tenant_id   UUID NOT NULL REFERENCES tenant(id),
  provider    TEXT NOT NULL,                              -- OPENAI | GEMINI | ANTHROPIC | OLLAMA
  model       TEXT NOT NULL,
  task_type   TEXT NOT NULL,                              -- EXPLAIN | REFACTOR | JAVADOC | CHAT
  tokens_in   INT NOT NULL,
  tokens_out  INT NOT NULL,
  cost_cents  INT NOT NULL,
  cache_hit   BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Relationships in words: a **tenant** has many **users**, **repositories**, and **analyses**. An **analysis** has many **file_results**; each has many **code_units**; a code_unit has many **issue_findings** and one optional **embedding**. Every **llm_call** is tied to a tenant for billing.

---

## 3. REST API contract (MVP)

Base path `/api/v1`. All except auth require an `Authorization: Bearer <JWT>` header. JSON in/out.

| Method | Path | Body | Returns | Notes |
|---|---|---|---|---|
| POST | `/auth/register` | `{email,password,tenantName}` | `{token}` | Creates tenant + admin user |
| POST | `/auth/login` | `{email,password}` | `{token}` | JWT valid ~1h |
| POST | `/repositories` | `{name,sourceType,sourceRef}` **or** multipart zip | `{repositoryId}` | ZIP = multipart upload |
| POST | `/analyses` | `{repositoryId}` | `202 {analysisId,status}` | Starts async job |
| GET | `/analyses/{id}` | — | `{status,healthScore,...}` | Poll or use SSE below |
| GET | `/analyses/{id}/events` | — | **SSE stream** | Live progress + stage |
| GET | `/analyses/{id}/files` | — | file tree + per-file metrics | For the left panel |
| GET | `/analyses/{id}/files/{fileId}` | — | source + findings for one file | For center + right panels |
| POST | `/analyses/{id}/chat` | `{question}` | `{answer,citedUnits[]}` | RAG (Phase 2) |
| GET | `/analyses/{id}/report?format=pdf\|md` | — | file download | Chronicle |
| GET | `/tenants/me/usage` | — | `{spentCents,budgetCents}` | Ledger |

**Error format** (all endpoints): `{ "code": "ANALYSIS_NOT_FOUND", "message": "...", "traceId": "..." }` with a proper HTTP status. Use `@RestControllerAdvice` for one global handler.

---

## 4. Module-by-module low-level design

### 4.1 Identity
- **Entities:** `Tenant`, `User`.
- **Key classes:** `AuthController`, `AuthService` (register/login), `JwtService` (sign/verify), `PasswordEncoder` (BCrypt), `SecurityConfig` (stateless, JWT filter).
- **Flow:** login → verify BCrypt hash → issue JWT containing `sub=userId`, `tenantId`, `role`. A `JwtAuthFilter` reads the header on every request and populates a `PraxisPrincipal`.
- **Public API:** `IdentityFacade.currentTenantId()`, `.currentUserId()` — used by other modules for tenant scoping.

### 4.2 Intake
- **Job:** turn a repo reference into a filtered set of `.java` files in a safe temp dir.
- **Key classes:** `SourceFetcher` (interface) → `GitSourceFetcher` (JGit shallow clone) and `ZipSourceFetcher` (extract with guards); `FileFilter` (drop non-`.java`, binaries, `.class`, honor `.gitignore`); `WorkspaceManager` (create/cleanup ephemeral dir).
- **Guards (must-have):** max total size, max file count, max single-file size, clone timeout, zip-bomb (compression-ratio) check. We **only parse**, never execute, downloaded code.
- **Output:** `WorkspaceRef { path, List<JavaFile> }` passed to Prism.

### 4.3 Prism (static analysis, no AI)
- **Parser:** JavaParser + **SymbolSolver** (configured with the source roots and JDK) so types resolve across files. Falls back to per-file parse if a project won't fully resolve.
- **Key classes:**
  - `AstParser` → produces `CompilationUnit`s.
  - `MetricCalculator` → per method/class: cyclomatic complexity, LOC, param count, class size, afferent/efferent coupling.
  - `PatternDetector` → AST visitors: `SingletonVisitor` (private ctor + static self-instance), `FactoryVisitor`, `BuilderVisitor`, `ObserverVisitor`; anti-patterns `GodObjectVisitor` (size+coupling threshold), `LongMethodVisitor`.
  - `RiskScorer` → combines metrics into a `risk_score` per `code_unit`. **This score decides what Cortex sees.**
- **Output:** persisted `file_result`, `code_unit`, and `STATIC` `issue_finding` rows.

### 4.4 Conductor (orchestration + background worker)
- **Job payload** (put on a Redis Stream): `{ analysisId, tenantId }`.
- **Worker loop:** consume job → run pipeline stages, updating `analysis.status` and emitting progress after each stage:
  ```
  FETCHING   (Intake)
  PARSING    (Prism: parse + metrics + patterns + risk)
  ANALYZING  (select code_units where risk_score >= THRESHOLD  →  Cortex)
  SUMMARIZING(Cortex: JavaDoc + explanations for selected units)
  SCORING    (Verdict)
  COMPLETE / FAILED
  ```
- **The funnel** lives here: `List<CodeUnit> targets = codeUnits.stream().filter(u -> u.riskScore() >= threshold).toList();` — never send everything.
- **Key classes:** `AnalysisOrchestrator`, `AnalysisWorker` (`@Scheduled`/stream consumer), `ProgressPublisher` (writes to a Redis pub/sub channel that the SSE controller relays), `AnalysisStateMachine`.
- **Reliability:** if a stage throws, set `FAILED` with a message; make stages idempotent so a retried job doesn't double-write (upsert by `source_hash`).

### 4.5 Cortex (LLM layer)
- **Provider abstraction:** program against Spring AI's `ChatModel`. Config picks the impl (OpenAI / Gemini / Anthropic / Ollama). If tenant is `LOCAL_ONLY`, force Ollama.
  > *Java analogy:* one interface, many implementations chosen at runtime — exactly like coding to `DataSource` and letting config pick the driver.
- **Key classes:**
  - `LlmClient` (interface) + `SpringAiLlmClient` (wraps `ChatModel`).
  - `ModelRouter` → picks model by `task_type` + tenant tier (cheap/local for JAVADOC & simple EXPLAIN; strong model for REFACTOR).
  - `PromptFactory` → versioned templates (`promptVersion` stored on the analysis) for tasks: `EXPLAIN`, `REFACTOR`, `JAVADOC`, `CHAT`.
  - `ResponseCache` → Redis; **key = `sha256(sourceHash + promptVersion + model)`**. Cache hit ⇒ zero cost, record `cache_hit=true` in Ledger.
  - `ResilientLlmClient` → Resilience4j retry + circuit breaker wrapper.
- **Every call** writes an `llm_call` row (tokens + cost) via Ledger.
- **Output:** `AI` `issue_finding` rows with `suggestion` text; generated JavaDoc attached to units.

### 4.6 Recall (RAG — Phase 2)
- On analysis, embed each `code_unit`'s source (via an embeddings model) → store `vector(768)`.
- Chat: embed the question → `SELECT ... ORDER BY vector <=> :q LIMIT 5` (pgvector cosine) → feed those units to Cortex as context → return grounded answer with cited units.

### 4.7 Verdict (scoring)
- `HealthScorer` combines: average complexity, coupling, count/severity of findings → `0..100`, mapped to A–F. **Keep the formula in one class, versioned, and documented** (managers will ask how it's computed).

### 4.8 Chronicle (reporting/API)
- Read-side controllers for the dashboard (`/files`, `/files/{id}`).
- `ReportGenerator` → builds Markdown, converts to PDF (e.g., Flying Saucer / OpenPDF). Streams the file for download.

### 4.9 Ledger (usage/cost)
- `UsageRecorder.record(LlmCallRecord)` after each LLM call.
- `BudgetGuard` → before an expensive call, check tenant remaining budget; block with a clear error if exceeded.

---

## 5. Frontend (React) structure

Vite + React + TypeScript + Monaco. Talks to the backend via REST + one SSE connection.

```
frontend/src/
├── api/            # fetch wrappers; attaches JWT; typed responses
├── auth/           # login/register, token storage (in-memory + refresh)
├── pages/
│   ├── LoginPage.tsx
│   ├── NewAnalysisPage.tsx     # paste GitHub URL or upload zip
│   └── AnalysisPage.tsx        # the 3-panel dashboard
├── components/
│   ├── FileTree.tsx            # left panel
│   ├── CodeViewer.tsx          # center: Monaco + line highlights for findings
│   ├── SuggestionPanel.tsx     # right: AI suggestions/patterns/JavaDoc
│   ├── HealthScoreBadge.tsx
│   └── ProgressBar.tsx         # driven by SSE stage events
└── hooks/
    ├── useAnalysis.ts          # start analysis, poll status
    └── useAnalysisEvents.ts    # EventSource → live stage/progress
```

**Live progress:** `useAnalysisEvents` opens `new EventSource('/api/v1/analyses/{id}/events')` and updates the progress bar as stages arrive. When `COMPLETE`, it fetches `/files` and renders the dashboard.

---

## 6. Cross-cutting concerns

- **Security:** stateless JWT; every DB query filtered by `tenant_id` from the token (never trust an id from the request body). Validate uploads (size/type). Parse-only, no execution.
- **Config over code:** provider keys, model names, thresholds, budgets all in `application.yml` / env — no secrets in code.
- **Observability:** Micrometer + `traceId` in every log line and error response. You *will* need traces to debug why an analysis is slow or expensive.
- **Testing:** unit-test Prism visitors against tiny Java snippets (deterministic, no AI); mock `LlmClient` for Cortex tests; a Modulith `verify()` test to guard boundaries.

---

## 7. Key configuration (`application.yml` shape)

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/praxis}
    username: ${DB_USER:praxis}
    password: ${DB_PASSWORD:praxis}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

praxis:
  jwt:
    secret: ${JWT_SECRET:change-me-32-bytes-minimum}
    ttl-minutes: 60
  llm:
    default-provider: ${LLM_PROVIDER:ollama}     # ollama | openai | gemini | anthropic
    openai-api-key: ${OPENAI_API_KEY:}
    gemini-api-key: ${GEMINI_API_KEY:}
    anthropic-api-key: ${ANTHROPIC_API_KEY:}
    ollama-base-url: ${OLLAMA_URL:http://localhost:11434}
  analysis:
    risk-threshold: 60          # only code_units at/above this go to the LLM
    max-repo-size-mb: 200
    max-files: 5000
```

---

## 8. Setup & execution flow

You'll containerize **later**, so this section has **two phases**: (A) develop locally now with only the *dependencies* in Docker, then (B) run the whole stack in Docker. Same app, same config keys — only the connection URLs change (which is why everything above reads from env vars).

### Prerequisites
- JDK 21, Node 20+, Docker Desktop, Git.
- (Optional, for local AI) Ollama, or an API key for one cloud provider.

### Phase A — Local development (do this first)

Run only Postgres and Redis in containers; run the backend and frontend directly for fast reloads.

**Step 1 — start the dependencies**
```bash
# Postgres with pgvector preinstalled
docker run -d --name praxis-pg -p 5432:5432 \
  -e POSTGRES_DB=praxis -e POSTGRES_USER=praxis -e POSTGRES_PASSWORD=praxis \
  pgvector/pgvector:pg16

# Redis
docker run -d --name praxis-redis -p 6379:6379 redis:7-alpine

# (optional) local AI model
# ollama serve   &&   ollama pull llama3.1
```

**Step 2 — run the backend** (Flyway auto-creates tables on startup)
```bash
cd backend
export JWT_SECRET="a-long-random-dev-secret-of-32+chars"
export LLM_PROVIDER=ollama          # or openai + OPENAI_API_KEY=...
./gradlew bootRun
# API now on http://localhost:8080
```

**Step 3 — run the frontend**
```bash
cd frontend
npm install
npm run dev                          # UI on http://localhost:5173, proxies /api → :8080
```

**Step 4 — smoke test**
1. Open `http://localhost:5173`, register a user.
2. Start an analysis with a small public Java GitHub URL.
3. Watch the progress bar move through the stages, then see the dashboard.

### Phase B — Full Docker (later)

When ready, run everything with one command. Create `docker/docker-compose.yml`:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment: { POSTGRES_DB: praxis, POSTGRES_USER: praxis, POSTGRES_PASSWORD: praxis }
    volumes: [ "pgdata:/var/lib/postgresql/data" ]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U praxis"]
      interval: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 10

  backend:
    build: { context: .., dockerfile: docker/backend.Dockerfile }
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/praxis   # service name, not localhost!
      DB_USER: praxis
      DB_PASSWORD: praxis
      REDIS_HOST: redis
      JWT_SECRET: ${JWT_SECRET}
      LLM_PROVIDER: ${LLM_PROVIDER:-ollama}
      OLLAMA_URL: http://ollama:11434
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
    ports: [ "8080:8080" ]

  frontend:
    build: { context: .., dockerfile: docker/frontend.Dockerfile }
    depends_on: [ backend ]
    ports: [ "3000:80" ]

  ollama:                       # optional local AI
    image: ollama/ollama
    volumes: [ "ollama:/root/.ollama" ]

volumes: { pgdata: {}, ollama: {} }
```

**Execution flow (startup order) — why it's ordered this way:**
1. **postgres** and **redis** start first and must pass their **healthcheck** (data stores must be ready before anything connects).
2. **backend** waits for both (`depends_on: condition: service_healthy`), then boots — Flyway runs migrations, the app connects using the **service names** (`postgres`, `redis`) as hostnames on the compose network.
3. **frontend** waits for backend, then serves the built React app via nginx, proxying `/api` to `backend:8080`.
4. **ollama** starts alongside; pull a model once with `docker compose exec ollama ollama pull llama3.1`.

**Run it:**
```bash
cd docker
export JWT_SECRET="a-long-random-prod-secret"
docker compose up --build
# UI: http://localhost:3000   API: http://localhost:8080
```

**The only thing that changes between Phase A and B** is host names: `localhost` → the compose service names (`postgres`, `redis`, `ollama`). Because every URL comes from an env var, your code never changes.

**Common pitfalls:**
- Using `localhost` inside a container to reach another container — use the **service name** instead.
- Backend starting before Postgres is ready — the healthcheck + `depends_on` fixes this.
- Forgetting the `pgvector` image — a plain `postgres` image lacks the `vector` type and Flyway's `CREATE EXTENSION vector` will fail.
- JWT secret too short — must be ≥32 bytes for signing.

---

### Build order recap
Identity → Intake → Prism → (wire) Conductor → Cortex → Verdict → Chronicle → dashboard. Get one public repo analyzed end-to-end **before** adding Recall (RAG/chat), Ledger budgets, or private-repo OAuth.
