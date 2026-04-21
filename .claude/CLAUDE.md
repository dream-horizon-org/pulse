# Pulse — Claude Code Instructions

Pulse is a real-time mobile + web observability platform built on OpenTelemetry.

## Monorepo Layout

| Directory | Service | Tech | Port |
|-----------|---------|------|------|
| `backend/server/` | REST API | Java 17, Vert.x 4.5, Guice, Maven | 8080 |
| `backend/pulse-alerts-cron/` | Alert evaluation cron | Java/Vert.x | 4000 |
| `backend/ingestion/` | OTEL Collector configs + ClickHouse schema | YAML/SQL | — |
| `pulse-ui/` | Dashboard | React 18, TypeScript, Mantine v7 | 3000 |
| `pulse_ai/` | AI agent | Python, Google ADK + Gemini | 8000 |
| `pulse-android-otel/` | Android SDK | Kotlin, OpenTelemetry | — |
| `pulse-react-native-otel/` | React Native SDK | TypeScript | — |
| `pulse-web-otel/` | Web SDK | TypeScript, OTLP | — |
| `deploy/` | Docker Compose, scripts, Terraform | — | — |

## Data Flow

```
Mobile/Web SDKs → OTEL Collector (4317/4318) → ClickHouse (otel DB)
Custom Events → Vector (14317/14318) → S3 (Parquet) → Athena
```

## Databases

- **MySQL 8** (`pulse_db`): metadata — interactions, alerts, configs, symbol files
- **ClickHouse 24.8** (`otel`): analytics — `otel_traces`, `otel_logs`, `otel_metrics_gauge`, `stack_trace_events`, `interaction_heatmaps_daily`

## Auth

- **Production:** Google OAuth 2.0 → JWT (access 24h, refresh 30d)
- **Dev Mode** (`GOOGLE_OAUTH_ENABLED=false`): mock users `mock-user-1` / `mock-user-2`, project `default-project`, key `DEV_MODE_API_KEY`

## Build Commands

```bash
# Backend
cd backend/server && mvn clean install        # build + test
cd backend/server && mvn verify               # tests + checkstyle + JaCoCo

# Frontend
cd pulse-ui && yarn install && yarn start     # dev server :3000
cd pulse-ui && yarn build && yarn lint

# Web SDK
cd pulse-web-otel && yarn install && yarn build && yarn test

# AI Agent
cd pulse_ai && ./setup.sh                     # Docker, port 8000

# Full stack
cd deploy && ./scripts/quickstart.sh          # build + start all
cd deploy && ./scripts/start.sh -d            # start detached
cd deploy && ./scripts/logs.sh [service]      # tail logs
cd deploy && ./scripts/stop.sh [-v]           # stop (optional: remove volumes)
```

## Key Ports

| Service | Port | Health |
|---------|------|--------|
| pulse-server | 8080 | `/healthcheck` |
| pulse-ui | 3000 | `/healthcheck.txt` |
| pulse-ai-agent | 8000 | `GET /health` |
| alerts-cron | 4000 | `/healthcheck` |
| ClickHouse | 8123 (HTTP), 9000 (native) | `SELECT 1` |
| MySQL | 3307 | `mysqladmin ping` |
| OpenFGA | 8180, 8181, 3001 | `/healthz` |
| OTEL Collector | 4317/4318, 13133 | — |
| MinIO | 9100 (S3), 9101 (console) | compose healthcheck |

## Commit Conventions

Format: `<type>(<scope>): <description>` — max 72 chars, imperative mood.

**Types:** `feat` `fix` `refactor` `docs` `test` `chore` `ci` `perf` `build`

**Scopes:** `backend` `ui` `ai` `android-sdk` `rn-sdk` `web-sdk` `deploy` `alerts-cron` `ingestion`

Examples:
```
feat(backend): add SLOW_RENDER_RATE alert metric
fix(ui): resolve date picker timezone offset in filters
```

## PR Workflow

Template (`.github/pull_request_template.md`): Summary → Context/Motivation → What Changed (by area: Backend / UI / SDK / Deploy) → Screenshots

Branch naming: `feat/*` · `fix/*` · `release/v*`

## Cross-Cutting Concerns

Alert metrics span: MySQL schema → backend service/DAO → ClickHouse query → alerts cron → UI form → AI registry. Changes typically touch multiple services.

## Rules Reference (loaded on demand per file type)

- Java backend: `.claude/rules/java-backend.md`
- React frontend: `.claude/rules/react-frontend.md`
- React testing: `.claude/rules/react-testing.md`
- ClickHouse SQL: `.claude/rules/clickhouse-sql.md`
- Docker/deploy: `.claude/rules/docker-deploy.md`
- Android SDK: `.claude/rules/android-sdk.md`
- React Native SDK: `.claude/rules/react-native-sdk.md`
- Web SDK: `.claude/rules/web-sdk.md`
- Python AI agent: `.claude/rules/python-ai-agent.md`
- Alerts cron: `.claude/rules/alerts-cron.md`

## Safety

- Never commit `.env` files — use `.env.example` as template
- Never run `rm -rf` without confirmation
- Never force-push to `main`
- Never run `reset-databases.sh` without explicit user confirmation

## Compact Instructions

When compacting history, preserve: API contracts, auth logic, schema changes, test failures and their fixes.
Discard: debug output, failed attempts, exploratory file reads.

## Caveman (team default)

Use **caveman** communication for natural-language replies: terse, high signal, no filler. Default intensity **full**. User can say `stop caveman` or `normal mode` to turn off for the session.

- Drop caveman briefly for security, irreversible ops, or when clarity needs full sentences; then resume.
- Code you write stays normal readable style.
- Commits / PR metadata: follow repo Conventional Commits + PR template; terse subjects OK within those rules.

Cursor loads the same policy from `.cursor/rules/caveman.mdc`.

Inspired by [caveman](https://github.com/JuliusBrussee/caveman) (MIT).
