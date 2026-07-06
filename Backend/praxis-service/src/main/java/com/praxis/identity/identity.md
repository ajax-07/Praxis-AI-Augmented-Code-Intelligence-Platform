# Praxis Backend — Identity Module (complete implementation)

This is a real, compilable Gradle/Spring Boot project — not pseudocode. It implements the **Identity** module fully, plus empty boundary stubs for the other 8 modules so `ModularityTests` has something to verify against from day one.

## What's implemented

| Layer | File | Purpose |
|---|---|---|
| Domain | `identity/domain/Tenant.java`, `User.java`, + 3 enums | JPA entities mapped to `tenant` / `app_user` |
| Public API | `identity/api/IdentityFacade.java`, `PraxisPrincipal.java`, `dto/*` | The ONLY types other modules may ever import from Identity |
| Internal | `identity/internal/*` | Repositories, `JwtService`, `AuthService`, `JwtAuthFilter`, `IdentityFacadeImpl`, exceptions |
| Config | `identity/SecurityConfig.java` | Stateless JWT security chain |
| Web | `identity/web/AuthController.java` | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| Shared | `common/*` | `ApiException`, `GlobalExceptionHandler`, `ApiError` — used by every module |
| Migration | `db/migration/V1__init.sql` | Full schema (Identity owns `tenant`/`app_user`; other tables exist for later modules) |
| Tests | `ModularityTests`, `JwtServiceTest`, `AuthServiceTest` | Boundary check + unit tests (no DB required) |

## What's NOT implemented (by design, per the build guide)

Intake, Prism, Conductor, Cortex, Recall, Verdict, Chronicle, Ledger are **empty module boundaries only** (`package-info.java` with no classes). Fill them in the order given in `BACKEND-BUILD-GUIDE.md` Step 7.

## Running it

```bash
# 1. infra
docker run -d --name praxis-pg -p 5432:5432 \
  -e POSTGRES_DB=praxis -e POSTGRES_USER=praxis -e POSTGRES_PASSWORD=praxis \
  pgvector/pgvector:pg16
docker run -d --name praxis-redis -p 6379:6379 redis:7-alpine

# 2. run (needs a local Gradle 8.12+ install, or open in your IDE and run PraxisApplication directly —
#    the Gradle wrapper jar isn't included in this scaffold since it must be downloaded once with network access)
export JWT_SECRET="a-long-random-dev-secret-of-32-plus-characters"
gradle bootRun
# first time only, to get ./gradlew working locally:
gradle wrapper --gradle-version 8.12
```

## Smoke test

```bash
# Register (creates a tenant + admin user, returns a JWT)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@praxis.io","password":"password123","tenantName":"Acme Inc"}'

# -> {"token":"eyJ...","tokenType":"Bearer"}

# Log in with the same credentials
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@praxis.io","password":"password123"}'

# Try a protected endpoint without a token -> 401
curl -i http://localhost:8080/api/v1/analyses

# Same call with a token -> would succeed once Conductor exists
curl -H "Authorization: Bearer <token-from-above>" http://localhost:8080/api/v1/analyses
```

## Design decisions worth knowing

- **`tenantId` on `User` is a raw UUID column, not a JPA `@ManyToOne`.** Every query in every future module filters by tenant; a lazy-loaded association would hide that filter instead of making it explicit and cheap.
- **`IdentityFacadeImpl` throws `IllegalStateException`** if called outside an authenticated request — that's intentional; it's a programming error for another module to ask "who's calling?" on an unauthenticated path.
- **Password/email validation lives in the DTOs** (`@NotBlank`, `@Email`, `@Size`), not in `AuthService` — keeps validation declarative and lets `GlobalExceptionHandler`'s `MethodArgumentNotValidException` handler produce a consistent 400 response automatically.
- **`common` is the one `OPEN` Modulith module.** Every other module is `CLOSED` by default — this is what makes `ModularityTests` meaningful.
- **Run `./gradlew test` before writing any other module.** If it's green, your first vertical slice (Identity) and your architectural guardrail (Modulith boundaries) both work.

## Known gap to close before Conductor needs it

`IdentityFacade`'s `api` package isn't yet declared as a Spring Modulith **Named Interface**. Right now nothing calls it from another module, so `ModularityTests` passes trivially. The moment Conductor (or any module) needs `IdentityFacade.currentTenantId()`, add:

```java
@org.springframework.modulith.NamedInterface("api")
package com.praxis.identity.api;
```

to `identity/api/package-info.java`, so Modulith explicitly recognizes it as the sanctioned cross-module surface.
