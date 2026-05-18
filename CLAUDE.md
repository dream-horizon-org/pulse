# CLAUDE.md

Guidance for Claude Code in this repo. This is the **map**; per-area conventions live in `.agents/rules/*.mdc` (auto-loaded by file type; also reachable as `.cursor/rules/` and `.claude/rules/` via symlinks).

## What Pulse Is

Real-time mobile + web observability platform on OpenTelemetry. Mobile/web SDKs send OTLP signals → Collector → ClickHouse. A React dashboard lets teams drill into crashes, sessions, network, interactions, and web vitals.

## Hierarchical Docs Router (read this first for depth)

For deep context, follow this routing order — load only what you need, not everything:

1. **`docs/architecture.md`** — platform-wide architecture, data flow, storage split, multi-tenancy, auth, cross-SDK contract.
2. **`docs/components/MANIFEST.md`** — index of every component brief. Pick the component(s) relevant to the task.
3. **`docs/components/<component>.md`** — one-page brief: purpose, paths, build commands, key files.
4. **`docs/plans/<component>/index.md`** — deep-dive handbook table of contents for that component.
5. **`docs/plans/<component>/<sub-area>/<file>.md`** — sub-component handbook (sections: Purpose, Source location, Public surface, Internal design, Dependencies, Data contracts, Tests, History/decisions, Rebuild recipe). Load only the files relevant to the change.

If `docs/plans/<component>/` is the single source of truth, an engineer (or LLM) can rebuild that component from scratch using its handbook alone.

The rest of this CLAUDE.md (monorepo layout, build commands, conventions, safety) remains the fast-path reference for routine work — use the router above when you need depth, and prefer it over loading large source trees into context.

## Monorepo Layout

| Directory | Service | Tech | Port |
|---|---|---|---|
| `backend/server/` | REST API | Java 17, Vert.x 4.5, Guice, Maven | 8080 |
| `backend/pulse-alerts-cron/` | Alert cron | Java/Vert.x | 4000 |
| `backend/ingestion/` | OTEL Collector configs + ClickHouse schema | YAML/SQL | — |
| `pulse-ui/` | Dashboard | React 18, TS, Mantine v7 | 3000 |
| `pulse_ai/` | AI agent | Python, Google ADK + Gemini | 8000 |
| `pulse-android-otel/` | Android SDK | Kotlin, OTel | — |
| `pulse-ios-otel/` | iOS SDK | Swift | — |
| `pulse-react-native-otel/` | RN SDK | TypeScript | — |
| `pulse-web-otel/` | Web SDK (alpha) | TypeScript, OTLP | — |
| `deploy/` | Docker Compose, scripts | — | — |

Auxiliary: `backend/db/` (MySQL migrations), `backend/spark/` (batch jobs), `backend/{heatmap-screenshot,session-capture,session-replay}-*/` (specialty ingestion), `pulse-mcp/`, `pulse-mobile-benchmarks/`, `vector/`, `docs/`, `scripts/`, `product/` (PRDs + web panel).

## Data Flow

```
Mobile/Web SDKs → OTEL Collector (4317/4318) → ClickHouse (otel DB)
Custom Events   → Vector (14317/14318) → S3 (Parquet) → Athena
```

## Build & Dev Commands

```bash
# Backend
cd backend/server && mvn verify        # build + checkstyle + JaCoCo
mvn -Dtest=MyClass#myMethod test       # single test

# Frontend
cd pulse-ui && yarn install && yarn start    # :3000
yarn build && yarn lint && yarn test
yarn test --testPathPattern=ComponentName

# Web SDK (pulse-web-otel)
cd pulse-web-otel && yarn install
cd pulse-web-otel && yarn build
cd pulse-web-otel && yarn test
cd pulse-web-otel && yarn workspace ecommerce-demo dev   # React demo :3002
cd pulse-web-otel && yarn demo:docs                      # vanilla demo :3003

# AI Agent
cd pulse_ai && ./setup.sh              # :8000

# Full stack
cd deploy && ./scripts/quickstart.sh   # build + start all
./scripts/start.sh -d                  # detached
./scripts/logs.sh [service]            # server/ui/ai/cron/otel-collector
./scripts/stop.sh [-v]                 # -v removes volumes
```

## Auth

- **Prod:** Google OAuth 2.0 → JWT (access 24h, refresh 30d).
- **Dev** (`GOOGLE_OAUTH_ENABLED=false`): mock users `mock-user-1` / `mock-user-2`, project `default-project`, key `default-project_devkey01`.

## ClickHouse Essentials

DB `otel`. Key tables: `otel_traces`, `otel_logs`, `otel_metrics_gauge`, `stack_trace_events`, `interaction_heatmaps_daily`.

Always use materialized columns over map access:

| Column | Source attribute |
|---|---|
| `ProjectId` | `ResourceAttributes['project.id']` |
| `PulseType` | `SpanAttributes` / `LogAttributes['pulse.type']` |
| `Platform` | `ResourceAttributes['os.name']` |
| `AppVersion` | Traces: `ResourceAttributes['app.version']`. Logs: `ResourceAttributes['app.build_name']`. `stack_trace_events`: use column `AppVersion`. |
| `SessionId` | `SpanAttributes` / `LogAttributes['session.id']` |

Every query must include: `Timestamp` range, `LIMIT`, `ProjectId` filter. Use tenant credentials — never admin. Multi-tenant isolation via row policies per project. Detail: `clickhouse-sql.mdc`.

## Architecture Pointers

Per-area conventions live in `.agents/rules/*.mdc` (the same files appear under `.cursor/rules/` and `.claude/rules/` via symlinks). Highlights only:

- **Backend (`backend/server/`, `backend/pulse-alerts-cron/`)** — Layer order: Controller (`resources/<domain>/`) → Service (`service/<domain>/`, RxJava3) → DAO (`dao/<domain>/` with `Queries.java`) → SQL. Guice DI, Lombok, MapStruct, `ServiceError`-coded errors. Primary CRUD store: **MySQL** (`vertx-mysql-client`); ClickHouse is OTEL/analytics only. Multi-tenancy: `tenant/TenantContext` + `TenantFilter` resolve `projectId`/user from JWT. Authorization: **OpenFGA** (`config/OpenFgaConfig.java`). **JaCoCo: 80% on changed files.** Detail: `java-backend.mdc`, `alerts-cron.mdc`, `java-testing.mdc`.
- **Frontend (`pulse-ui/`)** — Folder-per-screen/component (`Name.tsx` + `Name.module.css` + `index.ts`). State: TanStack Query (server) + Zustand (client). Routing config in `routes/routes.tsx`; URL constants in `constants/Constants.ts`. **API: always `makeRequest<T>()` from `helpers/makeRequest/`** — never raw fetch. Detail: `react-frontend.mdc`, `react-testing.mdc`.
- **Android SDK (`pulse-android-otel/`)** — Kotlin, Gradle multi-module. Two package roots that must never mix: `io.opentelemetry.android.*` (upstream) and `com.pulse.*` (Pulse). Detail: `android-sdk.mdc`.
- **iOS SDK (`pulse-ios-otel/`)** — Swift, SwiftPM/CocoaPods.
- **RN SDK (`pulse-react-native-otel/`)** — TS strict. Single `Pulse` facade in `index.tsx`. Detail: `react-native-sdk.mdc`.
- **Web SDK (`pulse-web-otel/`)** — Package `@dreamhorizon/pulse-web` (alpha). All signals carry `platform = 'web'`. Skills: `web-sdk-instrument`, `web-sdk-e2e-matrix`, `web-sdk-ship`. Sub-agents: `pulse-web-sdk`, `web-otel-spec-audit-orchestrator`. Detail: `pulse-web-otel-contract.mdc`, `pulse-web-otel-conventions.mdc`, `web-sdk.mdc`.

## Web SDK

Data contract — every signal must carry `platform = 'web'`. Key `pulse.type` values (non-exhaustive): `session.start`, `session.end`, `device.crash`, `non_fatal`, **`network.<HTTP status>`** on outbound fetch/XHR client spans (e.g. `network.200`, `network.0`), `app.click`, `web_vital`, `screen_load`, `screen_session`. Web does **not** emit a separate `screen_interactive` span; time-to-interactive is attached to **`screen_load`** when Navigation Timing allows (`pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`).

Package manual (commands, test gates, doc index): **`pulse-web-otel/CLAUDE.md`**. SDK-core spec hub: **`pulse-web-otel/docs/sdk-core/SPEC.md`**. Host integration entry: **`pulse-web-otel/docs/instrumentations/integration/SPEC.md`**.

Non-trivial `pulse-web-otel/` work: use **`web-sdk-ship`** (`.agents/skills/web-sdk-ship/SKILL.md`) for the merge-ready checklist (tests, diff audit, doc sync).

## Common Tasks

Map task → skill → relevant rule(s). Run the skill rather than freelancing.

| Task | Skill | Key rule(s) |
|---|---|---|
| New REST endpoint | `add-api-endpoint` | `java-backend.mdc`, `java-testing.mdc` |
| New UI screen | `add-ui-screen` | `react-frontend.mdc`, `react-testing.mdc` |
| New shared UI component | `add-ui-component` | `react-frontend.mdc`, `react-testing.mdc` |
| New AI sub-agent / tool | `add-ai-sub-agent` | `python-ai-agent.mdc` |
| Alert metric (cross-cutting) | `add-alert-metric` | `alerts-cron.mdc`, `clickhouse-sql.mdc`, `java-backend.mdc` |
| ClickHouse schema change | `clickhouse-migration` | `clickhouse-sql.mdc` |
| Web SDK implementation / verification | `web-sdk-instrument`, `web-sdk-e2e-matrix`, `web-sdk-ship` | `pulse-web-otel-contract.mdc`, `pulse-web-otel-conventions.mdc`, `web-sdk.mdc` |
| Build/run a service locally | `deploy-service` | `docker-deploy.mdc` |
| PR review | `pr-review` | `pr-workflow.mdc`, `commit-conventions.mdc` |

## Workflows, Skills, Agents, Commands

`.cursor/{skills,agents,commands,rules}/` and `.claude/{skills,agents,commands,rules}/` symlink to `.agents/` — single canonical copy for both tools.

- **Skills** (`.cursor/skills/`) — prefer these over freelancing: `add-api-endpoint`, `add-ui-screen`, `add-ui-component`, `add-ai-sub-agent`, `add-alert-metric`, `clickhouse-migration`, `deploy-service`, `pr-review`, `web-sdk-instrument`, `web-sdk-e2e-matrix`, `web-sdk-ship`, `pulse-prd-author`, `pulse-prd-review`.
- **Sub-agents** (`.cursor/agents/`) — delegate by area: `backend-engineer`, `frontend-engineer`, `ai-agent-engineer`, `mobile-sdk-engineer`, `pulse-web-sdk`, `web-otel-spec-audit-orchestrator`, `devops-engineer`, `data-analyst`, `debugger`, `pr-reviewer`, `unit-test-author`, `backend-test-runner`, `mock-server-maintainer`.
- **Slash commands** (`.cursor/commands/`) — `quickstart`, `start/stop/check-services`, `view-logs`, `build-{backend,ui,ai}`, `run-{backend,ui}-tests`, `lint-ui`, `query-clickhouse`, `create-pr`, `review-my-changes`, `find-existing`, `onboard`, etc.

## Commits & PRs

`<type>(<scope>): <description>` — max 72 chars, imperative.
Types: `feat fix refactor docs test chore ci perf build`. Scopes: `backend ui ai android-sdk ios-sdk rn-sdk web-sdk deploy alerts-cron ingestion`.
Branches: `feat/*` · `fix/*` · `release/v*`. PR template: Summary → Context → What Changed → Screenshots. Detail: `commit-conventions.mdc`, `pr-workflow.mdc`.

## Safety

- Never commit `.env` — use `.env.example`.
- Never force-push to `main`.
- Never run `reset-databases.sh` without explicit user confirmation.

## Session Defaults

`.claude/CLAUDE.md` adds session defaults (e.g. **caveman** terse-reply mode); auto-loaded alongside this file.
