# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Pulse Is

Real-time mobile + web observability platform on OpenTelemetry. Mobile/web SDKs send OTLP signals → Collector → ClickHouse. A React dashboard lets teams drill into crashes, sessions, network, interactions, and web vitals.

## Monorepo Layout

| Directory | Service | Tech | Port |
|---|---|---|---|
| `backend/server/` | REST API | Java 17, Vert.x 4.5, Guice, Maven | 8080 |
| `backend/pulse-alerts-cron/` | Alert cron | Java/Vert.x | 4000 |
| `backend/ingestion/` | OTEL Collector configs + ClickHouse schema | YAML/SQL | — |
| `pulse-ui/` | Dashboard | React 18, TypeScript, Mantine v7 | 3000 |
| `pulse_ai/` | AI agent | Python, Google ADK + Gemini | 8000 |
| `pulse-android-otel/` | Android SDK | Kotlin, OpenTelemetry | — |
| `pulse-react-native-otel/` | React Native SDK | TypeScript | — |
| `pulse-web-otel/` | Web SDK (in progress) | TypeScript, OTLP | — |
| `deploy/` | Docker Compose, scripts | — | — |

## Data Flow

```
Mobile/Web SDKs → OTEL Collector (4317/4318) → ClickHouse (otel DB)
Custom Events   → Vector (14317/14318) → S3 (Parquet) → Athena
```

## Build & Dev Commands

```bash
# Backend — build + test
cd backend/server && mvn clean install
cd backend/server && mvn verify               # tests + checkstyle + JaCoCo
cd backend/server && mvn -Dtest=MyTestClass test   # single test class
cd backend/server && mvn -Dtest=MyClass#myMethod test  # single method

# Frontend
cd pulse-ui && yarn install && yarn start     # dev :3000
cd pulse-ui && yarn build && yarn lint
cd pulse-ui && yarn test                      # all tests
cd pulse-ui && yarn test --testPathPattern=ComponentName  # single test

# Web SDK (pulse-web-otel — once scaffolded)
cd pulse-web-otel && yarn install
cd pulse-web-otel && yarn build
cd pulse-web-otel && yarn test
cd pulse-web-otel && yarn workspace ecommerce-demo dev   # React demo :3002
cd pulse-web-otel && yarn demo:docs                      # vanilla demo :3003

# AI Agent
cd pulse_ai && ./setup.sh                     # start Docker on :8000

# Full stack
cd deploy && ./scripts/quickstart.sh          # build + start all
cd deploy && ./scripts/start.sh -d            # start detached
cd deploy && ./scripts/logs.sh [service]      # tail logs (server/ui/ai/cron/otel-collector)
cd deploy && ./scripts/stop.sh [-v]           # stop; -v removes volumes
```

## Auth

- **Production:** Google OAuth 2.0 → JWT (access 24h, refresh 30d)
- **Dev mode** (`GOOGLE_OAUTH_ENABLED=false`): mock users `mock-user-1` / `mock-user-2`, project `default-project`, key `default-project_devkey01`

---

## Backend Architecture (Java)

**Layer order:** Controller → Service (interface + impl) → DAO → SQL

```
org.dreamhorizon.pulseserver/
├── resources/<domain>/     Controller + MapStruct mapper + DTOs
├── service/<domain>/       Interface + impl/ (RxJava3 Single/Maybe/Completable)
├── dao/<domain>/           DAO + Queries.java (SQL constants) + row models
├── error/                  ServiceError enum (codes: BE1001)
├── module/                 Guice modules
└── verticle/               MainVerticle, RestVerticle
```

- Inject via: `@RequiredArgsConstructor(onConstructor = @__({@Inject}))` + `@Slf4j`
- SQL lives in `Queries.java` as `static final UPPER_SNAKE_CASE` constants
- Errors: `ServiceError.X.getException()` → `Response<T>` with `Error.of(code, message)`
- Lombok `@Data` on DTOs; `@JsonIgnoreProperties(ignoreUnknown = true)` on response DTOs
- Google Checkstyle: 140-char lines, 2-space indent, no wildcard imports
- Coverage: 35% overall, **80% on changed files** (JaCoCo); tests use JUnit 5 + Mockito + AssertJ, method names `should*`

**Alerts cron** (`backend/pulse-alerts-cron/`) follows identical conventions. `CronManager` schedules evaluation via Vert.x timers; `AlertsService` builds ClickHouse query URLs. Same ServiceError/RxJava3/Guice/Checkstyle.

---

## Frontend Architecture (React)

```
pulse-ui/src/
├── screens/        Page-level (one folder per route, folder = ScreenName/)
├── components/     Shared components (same folder pattern)
├── hooks/          useHookName/ with index.ts + useHookName.ts
├── stores/         Zustand (devtools middleware)
├── constants/      ROUTES, API_ROUTES, OTEL semconv
├── helpers/        makeRequest (handles 401 refresh), auth, cookies
└── theme/          Mantine config
```

Each screen/component folder: `index.ts` (barrel) + `Name.tsx` + `Name.module.css` + optional `Name.interface.ts` / `Name.constants.ts`.

- **State:** TanStack Query v5 for server state; Zustand for client state
- **Forms:** `react-hook-form`; **Routing:** React Router v6 routes in `ROUTES` constant
- **API:** always through `makeRequest<T>()` — never raw fetch; base URL `REACT_APP_PULSE_SERVER_URL`
- **Styles:** CSS modules preferred; Mantine CSS vars (`var(--mantine-spacing-md)`)
- **Tests:** Jest + React Testing Library; wrap with `MantineProvider`; mock at `makeRequest` level

---

## ClickHouse Schema

Database: `otel`. Key tables: `otel_traces`, `otel_logs`, `otel_metrics_gauge`, `stack_trace_events`, `interaction_heatmaps_daily`.

**Always use materialized columns over map access:**

| Column | Source attribute |
|---|---|
| `ProjectId` | `ResourceAttributes['project.id']` |
| `PulseType` | `SpanAttributes/LogAttributes['pulse.type']` |
| `Platform` | `ResourceAttributes['os.name']` |
| `AppVersion` | `ResourceAttributes['app.build_name']` |
| `SessionId` | `SpanAttributes/LogAttributes['session.id']` |

Every query must include: time-range on `Timestamp`, `LIMIT`, `ProjectId` filter. Use tenant credentials — never admin user in application code. Multi-tenant isolation enforced via row policies per project.

---

## Web SDK (`pulse-web-otel/`)

Package `@dreamhorizon/pulse-web`. Data contract — every signal must carry `platform = 'web'`. Key `pulse.type` values: `session.start`, `session.end`, `device.crash`, `non_fatal`, `http`, `app.click`, `web_vital`, `screen_load`, `screen_interactive`, `screen_session`.

Full file map, data contract tables, and phase-by-phase implementation spec: **`pulse-web-otel/web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md`**
Milestone index (exit-criteria summaries, verification commands, ClickHouse example query): **`pulse-web-otel/web-sdk-plan/v1/MILESTONES.md`**
Use `/web-sdk` skill for context-loaded implementation or verification.

---

## Android SDK (`pulse-android-otel/`)

Kotlin, OTel Android SDK, Gradle multi-module. Two package roots that must never mix:
- `io.opentelemetry.android.*` — OTel-upstream
- `com.pulse.*` — Pulse-specific API, semconv, sampling

Instrumentations use `@AutoService(AndroidInstrumentation::class)` for automatic discovery. `pulse.type` values mirror web SDK (`screen_session`, `device.crash`, `app.click`, etc.).

---

## React Native SDK (`pulse-react-native-otel/`)

TypeScript strict. Single `Pulse` facade exported from `index.tsx`. Call `isSupportedPlatform()` before any native bridge call. Lefthook runs lint + typecheck on pre-commit.

---

## AI Agent (`pulse_ai/`)

Google ADK with Gemini. `agent.py` defines `root_agent`. Tools are plain functions returning `{"status": ..., "data": ...}` wrapped in `FunctionTool`. Sub-agent types: `SequentialAgent`, `ParallelAgent`, `LoopAgent`. Requires `GOOGLE_API_KEY` env var.

---

## Cross-Cutting: Adding an Alert Metric

Touches all of these in order: MySQL schema → `backend/server/` DAO/service → ClickHouse query → `backend/pulse-alerts-cron/` → `pulse-ui/` alert form → `pulse_ai/` registry.

---

## Commit & PR Conventions

Format: `<type>(<scope>): <description>` — max 72 chars, imperative mood.

Types: `feat` `fix` `refactor` `docs` `test` `chore` `ci` `perf` `build`
Scopes: `backend` `ui` `ai` `android-sdk` `rn-sdk` `web-sdk` `deploy` `alerts-cron` `ingestion`

Branch naming: `feat/*` · `fix/*` · `release/v*`
PR template: Summary → Context/Motivation → What Changed (Backend / UI / SDK / Deploy) → Screenshots

---

## Safety

- Never commit `.env` — use `.env.example`
- Never force-push to `main`
- Never run `reset-databases.sh` without explicit user confirmation

## Rules Reference

Detailed conventions loaded per file type:
- Java backend/cron: `.claude/rules/java-backend.md`, `.claude/rules/alerts-cron.md`
- Web SDK: `.claude/rules/web-sdk.md`
- React UI: `.claude/rules/react-frontend.md`, `.claude/rules/react-testing.md`
- ClickHouse: `.claude/rules/clickhouse-sql.md`
- Android SDK: `.claude/rules/android-sdk.md`
- React Native SDK: `.claude/rules/react-native-sdk.md`
- Python AI: `.claude/rules/python-ai-agent.md`
- Docker/deploy: `.claude/rules/docker-deploy.md`

## Compaction

Preserve: API contracts, auth logic, schema changes, test failures and fixes.
Discard: debug output, failed attempts, exploratory reads.
