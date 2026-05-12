# domains / alert

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [notification](notification.md), [query](query.md)

## Purpose

Alert definitions, evaluation, severities, scopes, snooze, tags, and
notification-channel binding.

## Source

- `resources/alert/AlertController.java` (root `@Path("/v1/alert")`)
- `resources/alert/AlertMapper.java`
- `resources/alert/enums/`, `resources/alert/models/`
- `resources/alert/v1/` — one Jakarta resource class per endpoint
- `service/alert/core/AlertService.java`,
  `AlertEvaluationService.java`, `AlertCronService.java`
- `service/alert/core/operatror/`, `service/alert/core/util/`,
  `service/alert/core/models/`
- `dao/AlertsDao.java`, `dao/query/AlertsQuery.java`

## Public surface

| Method | Path | File |
|---|---|---|
| GET | `/alerts` | `v1/GetAllAlerts.java` |
| GET | `/v1/alert/` (list) | `v1/GetAlerts.java` |
| GET | `/v1/alert/{id}` | `v1/GetAlertDetails.java` |
| POST | `/v1/alert` | `AlertController.java` |
| PUT | `/v1/alert` | `AlertController.java` |
| DELETE | `/v1/alert/{id}` | `v1/DeleteAlert.java` |
| POST | `/v1/alert/evaluateAndTriggerAlert` | `v1/EvaluateAndTriggerAlert.java` |
| GET | `/v1/alert/{id}/evaluationHistory` | `v1/GetAlertEvaluationHistory.java` |
| GET | `/v1/alert/filters` | `v1/GetAlertFilters.java` |
| GET | `/v1/alert/metrics` | `v1/GetAlertMetrics.java` |
| GET | `/v1/alert/scopes` | `v1/GetAlertScopes.java` |
| GET / POST | `/v1/alert/severity` | `v1/GetAlertSeverityList.java`, `CreateAlertSeverity.java` |
| GET / POST / DELETE / PUT | `/v1/alert/notificationChannels[/{id}]` | `v1/*AlertNotificationChannel*.java` |
| POST | `/v1/alert/{id}/snooze`, DELETE | `v1/SnoozeAlert.java`, `DeleteSnooze.java` |
| GET / POST | `/v1/alert/tag` | `v1/GetAllTags.java`, `CreateTag.java` |
| GET / POST / DELETE | `/v1/alert/{alert_id}/tag` | `v1/GetTagsForAlert.java`, `AddTagToAlert.java`, `DeleteTagFromAlert.java` |

DTOs: see `resources/alert/models/` and `service/alert/core/models/`.

## Internal design

- Controller hands off to `AlertService` (in `core/`).
- Evaluation uses `AlertEvaluationService` which builds ClickHouse queries
  (see `dao/query/AlertsQuery.java`) and applies operators in
  `operatror/`.
- `AlertCronService` is invoked separately by `backend/pulse-alerts-cron/`
  (this service exposes the eval primitives).
- Mapping via MapStruct `AlertMapper`.

## Dependencies

MySQL alert tables; ClickHouse for metric evaluation;
[notification](notification.md) for channel bindings; `pulse-alerts-cron`
consumes the same MySQL state.

## Data contracts

MySQL tables (per `AlertsDao` / `Queries`): alerts, alert_scopes,
alert_severities, alert_tags, alert_tag_map, alert_notification_channels,
alert_snooze, alert_evaluation_history.

Errors: generic `BE10xx`; `404` on not-found.

## Tests

`src/test/java/.../resources/alert/*`,
`.../resources/v1/*Alert*`, `.../service/alert/*`.

## Rebuild recipe

1. One Jakarta resource per endpoint under `resources/alert/v1/`.
2. Mount root `@Path("/v1/alert")` controller for create/update.
3. `AlertService` interface + impl, evaluation operators in `operatror/`.
4. `AlertsDao` + SQL constants in `Queries`.
5. Hook to `pulse-alerts-cron` via shared MySQL schema.
