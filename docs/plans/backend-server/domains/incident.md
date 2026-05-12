# domains / incident

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [alert](alert.md), [notification](notification.md)

## Purpose

Incident lifecycle (created when an alert fires) + Slack interactive handler.

## Source

- `resources/incident/IncidentController.java`
  (`@Path("/v1/incidents")`)
- `resources/incident/SlackWebhookController.java`
  (`@Path("/v1/incidents/slack/interactive")`)
- `resources/incident/RestIncidentMapper.java`
- `resources/incident/models/`
- `service/incident/IncidentService.java`, `IncidentServiceImpl.java`
- `dao/incidentdao/IncidentDao.java`, `IncidentQueries.java`,
  `dao/incidentdao/models/`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/incidents` |
| POST | `/v1/incidents` |
| POST | `/v1/incidents/slack/interactive` |

## Internal design

- `IncidentService` reads/writes MySQL via `IncidentDao`.
- Slack interactive webhook updates incident status (ack/resolve) based on
  payload action.
- Notifications fan-out delegated to [notification](notification.md).
- On-call routing via `service/oncall/` providers (`GoAlertOnCallProvider`
  or `NoOpOnCallProvider`).

## Dependencies

MySQL incident tables; Slack signature verification; [alert](alert.md)
(parent), [notification](notification.md), `service/oncall/*`.

## Data contracts

MySQL: `incidents(id, project_id, alert_id, status, severity, created_at, ...)`.
Errors via `ServiceError`.

## Tests

`src/test/java/.../resources/incident/*`,
`.../service/incident/*`.

## Rebuild recipe

1. `IncidentController` (GET list, POST create).
2. `SlackWebhookController` validates Slack signing secret then mutates state.
3. Bind `IncidentService` → `IncidentServiceImpl` in `MainModule`.
4. DAO + `IncidentQueries` for MySQL.
