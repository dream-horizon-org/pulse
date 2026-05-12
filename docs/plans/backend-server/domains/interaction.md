# domains / interaction

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [heatmap](heatmap.md), [performance](performance.md), [screen](screen.md)

## Purpose

User-defined interactions (named event sequences), suggested interactions,
filter options, telemetry filters, root-cause lookup.

## Source

- `resources/interaction/InteractionController.java`
  (`@Path("/v1/interactions")`)
- `resources/interaction/InteractionConfigController.java`
  (`@Path("/v1/interaction-configs")`)
- `resources/interaction/RestInteractionMapper.java`
- `resources/interaction/models/`, `resources/interaction/validators/`
- `service/interaction/InteractionService.java` +
  `impl/InteractionServiceImpl.java`, `impl/InteractionMapper.java`
- `service/interaction/UploadInteractionDetailService.java`,
  `UploadInteractionMapper.java`
- `service/interaction/ClickhouseMetricService.java`,
  `PerformanceMetricService.java`, `DateTimeUtils.java`,
  `InteractionTelemetryConstants.java`, `models/`
- `dao/interaction/InteractionDao.java`, `BaseInteractionDao.java`,
  `DaoInteractionMapper.java`, `Queries.java`, `models/`
- `dao/suggestedinteraction/SuggestedInteractionDao.java`, `Queries.java`
- `module/InteractionModule.java`,
  `module/UploadInteractionDetailModule.java`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/interactions` |
| POST | `/v1/interactions` |
| GET | `/v1/interactions/suggestions` |
| PUT | `/v1/interactions/suggestions/{id}/dismiss` |
| PUT | `/v1/interactions/suggestions/{id}/activate` |
| DELETE | `/v1/interactions/{name}` |
| PUT | `/v1/interactions/{name}` |
| GET | `/v1/interactions/{name}` |
| GET | `/v1/interactions/filter-options` |
| GET | `/v1/interactions/telemetry-filters` |
| GET | `/v1/interactions/{name}/root-cause` |
| GET | `/v1/interaction-configs` |

## Internal design

- Two controllers (interactions vs config). Validators in `validators/`
  enforce sequence uniqueness — `DUPLICATE_SUGGESTED_INTERACTION` 409 if
  matched.
- Service composes ClickHouse metric queries via `ClickhouseMetricService`.
- Suggestions DAO surfaces auto-detected candidates.
- Root-cause endpoint shells out to `service/rca/` + `service/rootcause/`
  helpers.

## Dependencies

MySQL interactions tables; ClickHouse for metrics; RCA modules
(`RcaModule`); [performance](performance.md) shares
`PerformanceMetricService`.

## Data contracts

MySQL: `interactions`, `suggested_interactions`, `interaction_configs`.
ClickHouse: `otel_traces` filtered by `PulseType=app.click` /
`screen_load`, plus `interaction_heatmaps_daily`.

Errors: `DUPLICATE_INTERACTION_NAME_ERROR` (500),
`DUPLICATE_SUGGESTED_INTERACTION` (409).

## Tests

`src/test/java/.../resources/interaction/*`,
`.../service/interaction/*`.

## Rebuild recipe

1. Controllers above with validators for body.
2. `InteractionService` + impl in `impl/`; `Queries.java` for MySQL SQL.
3. `ClickhouseMetricService` + `PerformanceMetricService` for analytics.
4. Suggested-interaction DAO + dismiss/activate flow.
5. Install `InteractionModule`, `UploadInteractionDetailModule`.
