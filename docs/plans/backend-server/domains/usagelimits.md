# domains / usagelimits

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [tenants](tenants.md), [tiers](tiers.md), [notification](notification.md)

## Purpose

Per-project usage limits + counters: read by UI, mutate via internal service,
sync to Redis (Kong-enforced), and emit notifications when thresholds cross.

## Source

- `resources/usagelimits/ProjectUsageLimitsController.java`
  (`@Path("/v1/projects/{projectId}/limits")`)
- `resources/usagelimits/InternalUsageLimitsController.java`
  (`@Path("/internal/v1/projects")`)
- `resources/usagelimits/UsageLimitMapper.java`
- `resources/usagelimits/models/`
- `service/usagelimit/UsageLimitService.java`,
  `UsageLimitValidator.java`, `models/`
- `service/kong/KongUsageCreditsRedisSyncService.java`
- `service/cron/UsageLimitNotificationProcessService.java`
- `dao/usagelimit/ProjectUsageLimitDao.java`,
  `ProjectUsageLimitQueries.java`, `models/`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/projects/{projectId}/limits` |
| GET | `/internal/v1/projects/{projectId}/limits` |
| GET | `/internal/v1/projects/limits` |
| POST | `/internal/v1/projects/limits/sync-to-redis` |
| POST | `/internal/v1/projects/limits/process-usage-notifications` |
| PUT | `/internal/v1/projects/{projectId}/limits` |
| POST | `/internal/v1/projects/{projectId}/limits/reset` |
| GET | `/internal/v1/projects/{projectId}/limits/history` |
| POST | `/internal/v1/projects/{projectId}/limits/notifications` |

## Internal design

- `UsageLimitService` reads tier defaults + project overrides, validates via
  `UsageLimitValidator`.
- `KongUsageCreditsRedisSyncService` exports remaining credits to Redis so
  the gateway can throttle ingestion.
- `UsageLimitNotificationProcessService` is invoked by cron; raises
  notifications when thresholds (e.g. 80/100%) cross.

## Dependencies

MySQL (`project_usage_limits`, history), Redis (Kong), [tiers](tiers.md)
defaults, [notification](notification.md) for threshold alerts.

## Data contracts

MySQL: `project_usage_limits(project_id, kind, hard_cap, soft_cap,
period_start, used)`, `project_usage_limit_history(...)`.

## Tests

`src/test/java/.../resources/usagelimits/*`,
`.../service/usagelimit/*`, `.../service/cron/*`,
`.../service/kong/*`.

## Rebuild recipe

1. Public read controller for `/v1/projects/{id}/limits`.
2. Internal controller for batch list, sync, reset, notifications, history.
3. `UsageLimitService` + `Validator`.
4. Cron service processes thresholds; Kong sync service pushes to Redis.
5. DAO + Queries against MySQL.
