# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Is Pulse

Pulse is a real-time **mobile observability platform** built on OpenTelemetry. It consists of:

- `backend/server` — Java/Vert.x REST API (port 8080)
- `backend/pulse-alerts-cron` — Cron-based alert evaluation service (port 4000)
- `pulse-ui` — React 18 dashboard (port 3000)
- `pulse_ai` — Python/Google ADK AI analysis agent (port 8000)
- `pulse-android-otel` — Kotlin Android SDK
- `pulse-react-native-otel` — TypeScript React Native SDK
- `deploy/` — Docker Compose orchestration

## Build & Run Commands

### Full Stack (Docker)
```bash
cd deploy
./scripts/quickstart.sh        # Build and start everything
./scripts/start.sh             # Start all services
./scripts/stop.sh              # Stop all services
./scripts/logs.sh [service]    # View logs for a service
./scripts/reset-databases.sh   # Wipe MySQL + ClickHouse data
```

### Backend (Java/Maven)
```bash
cd backend/server
mvn clean package              # Build JAR
mvn test                       # Run unit tests
mvn verify                     # Run all tests + Checkstyle
mvn jacoco:report              # Generate coverage report

# Run a single test class
mvn -Dtest=InteractionServiceTest test

# Run a single test method
mvn -Dtest=InteractionServiceTest#shouldThrowExceptionIfInteractionAlreadyPresent test
```

### Frontend (React/Yarn)
```bash
cd pulse-ui
corepack enable                # Enable Yarn 4 (first time only)
yarn install
yarn start                     # Dev server at http://localhost:3000
yarn build                     # Production build
yarn lint                      # ESLint
yarn format                    # Prettier
yarn test                      # Jest (all)
yarn test --testNamePattern="name"   # Single test
yarn test --watch              # Watch mode
```

### AI Agent (Python)
```bash
cd pulse_ai
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env           # Add GOOGLE_API_KEY
adk web                        # Start dev server at port 8000
```

### Android SDK (Kotlin)
```bash
cd pulse-android-otel
./gradlew assemble             # Build SDK
./gradlew check                # Tests + checks
./gradlew spotlessApply        # Format code
```

## Backend Architecture

### Package Structure (`org.dreamhorizon.pulseserver/`)

```
resources/      # JAX-RS REST controllers, grouped by domain
service/        # Business logic interfaces + impls
dao/            # Data access objects + SQL queries
client/         # External clients (ClickHouse, MySQL, Athena, S3)
verticle/       # Vert.x verticles (MainVerticle, RestVerticle)
module/         # Guice modules (*Module.java)
error/          # ServiceError enum (codes like BE1001)
```

**Verticle pattern**: `MainVerticle` loads config and stores it in `SharedDataUtils`, then deploys one `RestVerticle` per CPU core. `RestVerticle` extends `AbstractRestVerticle`, scans for JAX-RS resources, and configures Guice + CORS.

**Reactive pattern**: All service methods return RxJava3 `Single<T>`, `Maybe<T>`, or `Completable` — never block the event loop. Bridge to Vert.x using `RestResponse.jaxrsRestHandler()`.

**Dependency injection**: Guice. Use `@RequiredArgsConstructor(onConstructor = @__({@Inject}))`.

**File naming conventions**:
- Controllers: `*Controller`, DAOs: `*Dao`, Services: `*Service` / `*ServiceImpl`
- MapStruct mappers: `Rest*Mapper` (REST ↔ service), `Dao*Mapper` (DAO ↔ service)
- SQL constants: `Queries.java` with `UPPER_SNAKE_CASE` static fields
- Guice modules: `*Module`

**Error handling**: Use `ServiceError` enum — throw via `ServiceError.X.getException()`. Response shape: `Response<T>` with `data` and `Error.of(code, message)`.

**Code style**: Google Checkstyle, 140-char lines, 2-space indent, no wildcard imports. Lombok `@Data`, `@Slf4j`.

**Testing**: JUnit 5 + Mockito + AssertJ. Test method naming: `should*`. Use `@Nested` for logical groups. JaCoCo enforces **35% overall** / **80% on changed files**.

## Frontend Architecture

### Structure (`pulse-ui/src/`)

```
screens/        # Page-level screens (one folder per route)
components/     # Shared reusable components
hooks/          # Custom React hooks wrapping API calls
stores/         # Zustand client state stores
constants/      # API_ROUTES, ROUTES, OTEL semconv enums
helpers/        # makeRequest(), auth, cookies
types/          # Shared TypeScript types
```

**Screen/component folder pattern**: Each screen or component gets its own folder containing `index.ts` (barrel), `Name.tsx`, `Name.module.css`, optional `Name.interface.ts`, `Name.constants.ts`, and a `components/` subfolder for sub-components.

**Hook folder pattern**: `hooks/useHookName/` with `index.ts`, `useHookName.ts`, and `useHookName.interface.ts`.

**State**: TanStack Query v5 for server state; Zustand with `devtools` for client state; `react-hook-form` for forms.

**API calls**: Use `makeRequest<T>()` from `helpers/makeRequest/` — handles 401 token refresh. API routes live in `constants/Constants.ts` → `API_ROUTES`. New routes must also be added to `ROUTES` and `App.tsx`.

**UI**: Mantine v7 components, Tabler icons, `echarts-for-react` for charts, `mantine-datatable` for tables. Use CSS modules + Mantine CSS variables (`var(--mantine-spacing-md)`).

## Data Layer

### ClickHouse (Analytics DB)
Database: `otel`. Core tables: `otel_traces`, `otel_logs`, `otel_metrics_gauge`, `stack_trace_events`.

**Always use materialized columns** (e.g., `ProjectId`, `Platform`, `AppVersion`, `UserId`, `SessionId`) instead of accessing the underlying Map columns (`SpanAttributes`, `ResourceAttributes`) — they are indexed and faster.

**Multi-tenancy**: Each project gets a dedicated ClickHouse user + row policy filtering by `ProjectId`. Always query through the tenant user to enforce isolation.

**Query rules**: Always include a `Timestamp` range filter, always use `LIMIT`.

### MySQL (Metadata DB)
Used for user accounts, projects, alert configs, SDK configs, and ClickHouse tenant credentials. Managed with Flyway migrations under `backend/server/src/main/resources/db/migration/`.

## Authentication

- **Production**: Google OAuth 2.0 → JWT (24h access token, 30d refresh token)
- **Dev mode** (`GOOGLE_OAUTH_ENABLED=false`): Mock users pre-seeded in MySQL + OpenFGA
- Authorization: OpenFGA (RBAC), running on port 8180/8181

## Commit Conventions

Format: `<type>(<scope>): <short description>` (≤ 72 chars, imperative mood, no trailing period)

**Types**: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `ci`, `perf`, `build`

**Scopes**: `backend`, `ui`, `ai`, `android-sdk`, `rn-sdk`, `deploy`, `alerts-cron`, `ingestion`

Examples:
```
feat(backend): add SLOW_RENDER_RATE alert metric
fix(ui): resolve date picker timezone offset in filters
test(backend): add InteractionService unit tests
```

## Key Ports

| Service | Port |
|---------|------|
| pulse-server (API) | 8080 |
| pulse-ui | 3000 |
| pulse-ai | 8000 |
| alerts-cron | 4000 |
| MySQL | 3307 (Docker) / 3306 |
| ClickHouse HTTP | 8123 |
| ClickHouse native | 9000 |
| OTEL Collector gRPC | 4317 |
| OTEL Collector HTTP | 4318 |
| OpenFGA | 8180/8181 |
