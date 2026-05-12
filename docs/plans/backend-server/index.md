# backend-server — plan handbook

Sub-component handbook for `backend/server/`. Brief: [`/docs/components/backend-server.md`](../../components/backend-server.md).

## Summary

A Vert.x 4.5 + Guice REST service exposing `/v1/*` (public) and `/internal/v1/*`
(service-to-service) endpoints. Layering is **Controller → Service → DAO →
Queries.java**. Errors flow through `ServiceError` (`BE10xx`). State lives in
MySQL; analytics in ClickHouse + Athena.

## Reading order

1. `core/verticles.md` — process lifecycle
2. `core/guice-modules.md` — DI graph
3. `core/auth.md` — JWT + API key
4. `core/error-handling.md` — `ServiceError` enum
5. `core/mysql-access.md` — DAO + `Queries.java`
6. `core/clickhouse-access.md` — per-project credentials, pools
7. `domains/*.md` — one per REST domain

## Core

| File | Topic |
|---|---|
| [core/verticles.md](core/verticles.md) | `MainVerticle`, `RestVerticle`, ANR consumer, AI SSE proxy |
| [core/guice-modules.md](core/guice-modules.md) | `MainModule` + per-domain `module/*.java` |
| [core/error-handling.md](core/error-handling.md) | `ServiceError`, `RestError`, exception mapping |
| [core/auth.md](core/auth.md) | `AuthService`, `JwtService`, `VertxAuthChain`, OpenFGA |
| [core/mysql-access.md](core/mysql-access.md) | `MysqlClient`, DAO conventions, `Queries.java` |
| [core/clickhouse-access.md](core/clickhouse-access.md) | `ClickhouseProjectService`, pool manager, read/write clients |

## Domains

| File | REST root | Notes |
|---|---|---|
| [domains/alert.md](domains/alert.md) | `/v1/alert*`, `/alerts` | Alert CRUD, eval, channels, tags |
| [domains/analytics.md](domains/analytics.md) | `/internal/analytics/*` | Funnel/journey batch |
| [domains/apikeys.md](domains/apikeys.md) | `/v1/projects/{projectId}/api-keys`, `/internal/v1/api-keys` | Project API keys + Redis sync |
| [domains/breadcrumb.md](domains/breadcrumb.md) | `/v1/breadcrumbs` | Session breadcrumbs |
| [domains/eventdefinition.md](domains/eventdefinition.md) | `/v1/event-definitions`, `/v1/events` | Event catalog |
| [domains/heatmap.md](domains/heatmap.md) | `/v1/heatmap` | Click heatmaps |
| [domains/incident.md](domains/incident.md) | `/v1/incidents`, Slack interactive | Incidents |
| [domains/interaction.md](domains/interaction.md) | `/v1/interactions`, `/v1/interaction-configs` | Interactions + suggestions |
| [domains/logs.md](domains/logs.md) | `/v1/logs` | OTLP log ingestion |
| [domains/notification.md](domains/notification.md) | `/v1/notifications/*`, `/webhooks/ses` | Channels, templates, send |
| [domains/performance.md](domains/performance.md) | `/v1/interactions/performance-metric/distribution` | Latency dist |
| [domains/productAnalysis.md](domains/productAnalysis.md) | `/v1/funnels` + journey | Funnel/journey UI APIs |
| [domains/query.md](domains/query.md) | `/query/*` | Ad-hoc query engine |
| [domains/screen.md](domains/screen.md) | `/v1/screens/{name}/root-cause` | Screen RCA |
| [domains/session.md](domains/session.md) | `/v1/sessions/*`, `/api/v1/sessions` | Session listing, detail, replay |
| [domains/symbolicate.md](domains/symbolicate.md) | `/v1/symbolicate/file/upload` | Mapping file upload |
| [domains/tenants.md](domains/tenants.md) | `/v1/tenants`, `/internal/v1/tenants` | Tenants |
| [domains/tiers.md](domains/tiers.md) | `/v1/tiers`, `/internal/v1/tiers` | Tier defs |
| [domains/usagelimits.md](domains/usagelimits.md) | `/v1/projects/{id}/limits`, `/internal/v1/projects` | Usage limits |

## Rebuild checklist

To recreate the service from scratch:

1. Scaffold Maven module with `com.dream11.rest`, Vert.x 4.5, Guice, Lombok.
2. Add `MainApplication` -> `MainVerticle` -> `RestVerticle`.
3. Wire `MysqlClient`, `ClickhouseProjectConnectionPoolManager`, `WebClient` via
   `MainModule` + Guice multibindings for resources.
4. Add `ServiceError` enum and `RestError` mapping.
5. Add `VertxAuthChain` JWT + API key filter.
6. Scaffold each domain as `resources/<d>/`, `service/<d>/`, `dao/<d>/`.
7. Wire HOCON `src/main/resources/conf/*.conf`.
8. Add `mvn verify` (checkstyle, jacoco 35% / 80% changed-files).
