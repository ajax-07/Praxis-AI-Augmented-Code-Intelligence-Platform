# Praxis Console — Frontend

The React web client for [Praxis](../../README.md). It authenticates users, starts analyses (GitHub URL or zip upload), streams live pipeline progress over SSE, and renders the results dashboard: a file tree, a code viewer with severity-highlighted lines, and a findings panel with expandable AI suggestions.

> New to the project? Read the [root README](../../README.md) first for the big picture, then come back here for frontend specifics.

---

## Tech stack

| Concern | Choice | Notes |
|---|---|---|
| Framework | **React 19** + **Vite 6** | Fast dev server + HMR; lean production bundle |
| Language | **TypeScript** (strict) | `tsc` is the type gate in the build; every API DTO is explicitly typed |
| UI | **MUI 7** (+ Emotion) | Component library; light/dark theme via a token-based MUI theme |
| Server state | **TanStack Query 5** + **Axios** | Caching, polling, invalidation — the reason there's almost no global client state |
| Client state | **React Context** | Only auth, toasts, and color mode — no Redux |
| Icons | **Lucide React** | |
| Routing | **React Router 7** | |

**Why Query instead of Redux:** almost all state in this app *is* server state (analyses, files, findings). TanStack Query owns caching, background refetch, and invalidation for it, which shrinks genuinely-global client state down to three tiny concerns — auth token, toast queue, theme — each a small Context. Redux would be boilerplate with no payoff here.

---

## Architecture at a glance

```
src/
├── main.tsx / App.tsx     # providers (QueryClient, Auth, Theme, Toast) + routes
├── api/                   # the ONLY place that talks HTTP
│   ├── client.ts          # Axios instance, JWT interceptor, ApiError normalizer, central 401→logout
│   ├── auth.api.ts        # register, login
│   ├── analyses.api.ts    # start, upload (multipart + progress), get, list
│   ├── dashboard.api.ts   # files, fileDetail
│   └── sse.ts             # EventSource wrapper (?access_token=), closes cleanly on terminal
├── types/api.ts           # 1:1 mirror of the backend contract (DTOs + enums)
├── context/               # AuthContext · ToastContext · ColorModeContext
├── hooks/                 # data logic, kept out of components
│   ├── useAnalyses.ts     # history list
│   ├── useAnalysis.ts     # detail; polls every 3s while running (SSE fallback)
│   ├── useAnalysisEvents.ts   # SSE → writes into Query cache; invalidates on COMPLETE
│   ├── useStartAnalysis.ts    # start + upload mutations
│   └── useFiles.ts        # file tree + file detail
├── components/
│   ├── layout/            # AppShell, ProtectedRoute
│   ├── analysis/          # PipelineProgress, HealthScoreBadge, FileTree, CodeViewer, FindingsPanel, SeverityChip
│   └── common/            # ErrorBoundary, QueryError, Skeletons, EmptyState
├── pages/                 # Login, Register, AnalysesList, NewAnalysis, Analysis
└── utils/                 # jwt (decode claims), severity (color map), format
```

**The layering rule:** components never call `fetch`/`axios` directly. UI → hooks → `api/*` → backend. This keeps data logic testable and swappable, and means the whole HTTP surface is auditable in one folder.

### How live progress works

1. `POST /analyses` returns `202 {analysisId}`; the app navigates to `/analyses/:id`.
2. `useAnalysisEvents` opens an SSE stream to `/analyses/{id}/events?access_token=<jwt>` — the token rides in the query string because the browser `EventSource` API **cannot set an `Authorization` header** (the backend accepts it there only for `/events`).
3. Each `progress` frame is written straight into the `['analysis', id]` Query cache, so the whole page re-renders from one source of truth. On a terminal frame the results queries are invalidated and the dashboard loads.
4. `useAnalysis` also polls every 3 s **while the status is non-terminal** as a fallback — a dropped SSE stream can never strand the progress view.

---

## Local development

Requires **Node 20+**. The backend should be running on `http://localhost:8145` (see the [root setup guide](../../README.md#9-setup-guide-zero-to-running)).

```bash
npm install
npm run dev
# http://localhost:5173  — the dev server proxies /api → :8145 (see vite.config.ts)
```

No environment variables are needed: the app always calls a **relative `/api/...` URL**, which the Vite dev proxy (dev) or nginx (Docker) forwards to the backend. The same build artifact works in both environments.

### Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server with HMR + `/api` proxy |
| `npm run build` | `tsc` (strict type-check) then `vite build` → `dist/` |
| `npm run preview` | Serve the production `dist/` locally to sanity-check a build |

---

## Production build & Docker

`npm run build` type-checks and emits `dist/` with vendor-split chunks (react / mui / app) so returning browsers reuse cached vendor code across deploys.

The [`Dockerfile`](./Dockerfile) is multi-stage: Node builds the bundle, then **nginx serves it and reverse-proxies `/api` to `backend:8145`** ([`nginx.conf`](./nginx.conf)). nginx also disables buffering on the proxy so SSE progress frames flush instantly, and sets a body cap matching the backend's 200 MB upload limit.

You normally don't build this by hand — run it via compose: `docker compose --profile app up -d --build frontend` (from `Docker/`), which serves it on **http://localhost:5173** (same port as `npm run dev`) and proxies `/api` to the backend service. See the [root setup guide](../../README.md#9-setup-guide-zero-to-running).

---

## Conventions

- **Types mirror the backend.** `src/types/api.ts` is the single source of truth for request/response shapes and enums (`AnalysisStatus`, `Severity`, `FindingSource`). If a backend record changes, update it there.
- **Every network state is handled.** Loading → `Skeletons`; error → `QueryError` (with retry) or a toast for mutations; empty → `EmptyState`; render crash → `ErrorBoundary`. An empty AI-findings list is a **valid** state (clean file, or the LLM degraded to static-only), never an error.
- **Auth is stateless.** The JWT lives in `localStorage`; `AuthContext` decodes its claims and drops expired tokens on boot; any `401` on a protected call logs out centrally via the Axios interceptor.
- **The code viewer is intentionally not Monaco.** For read-only rendering with line highlights, a ~100-line component beats a 3 MB editor. The swap seam is `components/analysis/CodeViewer.tsx` if richer editing is ever needed.
