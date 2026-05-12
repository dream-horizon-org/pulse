# delivery / email (delegated)

Parent: [pulse-alerts-cron](../index.md) ·
Brief: [component](../../../components/pulse-alerts-cron.md)

## 1. Purpose

Document where alert email delivery actually happens. Email send is
owned by **pulse-server**; this cron service only triggers evaluation.

## 2. Trigger

Cron tick → POST
`{pulseServerUrl}/v1/alert/evaluateAndTriggerAlert?alertId={id}`. If
threshold breaches and the alert's notification config includes an
email channel, pulse-server enqueues and sends the email.

## 3. Cron-side responsibilities

- Stable interval ticking (see [cron-manager](../core/cron-manager.md)).
- Retry on 5xx so transient mail failures don't drop the alert.
- Surface failures in logs with `alertId` for ops triage.

## 4. Cron-side non-responsibilities

- No SMTP client, no templating, no recipient list — all in
  pulse-server.

## 5. Periodic email job

`PeriodicSyncService` does POST a `usageLimitNotification` endpoint on
`usageLimitNotificationIntervalSeconds` (default 86 400 = 24 h). The
endpoint is async (202), pulse-server uses `cron_jobs_history` to
dedupe.

## 6. Tests

Mock WebClient, assert URL + headers + retry pathway.

## 7. Observability

Log `alertId`, HTTP status, attempt count, elapsed ms.

## 8. Cross-links

- [cron-manager](../core/cron-manager.md)
- [alerts-service](../core/alerts-service.md)
- Sibling channels: [slack](./slack.md), [webhook](./webhook.md)

## 9. Open items

- Add metric: cron-side retry-exhausted count per alert.
- If email-only alerts grow, consider separate priority lane.
