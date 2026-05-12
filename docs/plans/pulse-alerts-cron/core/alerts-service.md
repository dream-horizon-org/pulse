# alerts-service

Parent: [pulse-alerts-cron](../index.md) ·
Brief: [component](../../../components/pulse-alerts-cron.md)

## 1. Purpose

Fetch the list of active alerts from pulse-server and decorate each
with the evaluation URL that `CronManager` will POST to per tick.

## 2. Source

- `services/AlertsService.java`
- `dto/response/AlertsResponseDto.java`
- `models/Alert.java`
- `config/ApplicationConfig.java` → `pulseServerUrl`

## 3. Contract

- Outbound: `GET {pulseServerUrl}/alerts`, headers
  `Content-Type: application/json`, `Accept: application/json`.
- Inbound shape: `AlertsResponseDto { data: { alerts: List<Alert> } }`.
- Per alert: `setUrl(pulseServerUrl + "/v1/alert/evaluateAndTriggerAlert?alertId=" + alertId)`.
- Returns `Single<List<Alert>>`.

## 4. Error handling

`onErrorResumeNext` wraps failures in
`new Exception("Failed to fetch alerts : " + err)` and logs. Callers
(`DataSyncService`, `PeriodicSyncService`) decide whether to retry or
fail boot.

## 5. Why not ClickHouse here

By design, this service does **not** open ClickHouse connections.
Evaluation lives in pulse-server so:

- Tenant credentials + row policies are managed in one place.
- Query SQL evolves with the alert form in the UI without a
  cross-service contract change.
- This service stays small and stateless (no JDBC pool).

## 6. Caching

Currently re-fetches each sync cycle. If alert counts grow, add ETag /
`If-Modified-Since` on pulse-server side; cache `List<Alert>` in
`DataSyncService` between syncs.

## 7. Tests

- Mock `WebClient`, assert URL decoration and error wrapping.
- AssertJ + Mockito + JUnit 5 per repo rules.

## 8. Cross-links

- Consumer: [cron-manager](./cron-manager.md)
- Eval contract: [clickhouse-evaluation](./clickhouse-evaluation.md)

## 9. Open items

- Surface alert count + last-sync timestamp on `/health`.
- Add typed error codes via `ServiceError` instead of raw `Exception`.
