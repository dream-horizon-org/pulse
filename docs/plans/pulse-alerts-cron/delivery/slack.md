# delivery / slack (delegated)

Parent: [pulse-alerts-cron](../index.md) ·
Brief: [component](../../../components/pulse-alerts-cron.md)

## 1. Purpose

Slack channel dispatch is owned by **pulse-server**. This cron service
only triggers evaluation; the breach decision and Slack webhook call
happen server-side.

## 2. Trigger

Cron tick → POST
`{pulseServerUrl}/v1/alert/evaluateAndTriggerAlert?alertId={id}`. If
the alert's notification config includes a Slack channel, pulse-server
calls the webhook.

## 3. Cron-side responsibilities

- Tick on time (see [cron-manager](../core/cron-manager.md)).
- Retry 5xx evaluation responses; do **not** retry on 4xx.

## 4. Failure isolation

A Slack webhook outage must not stall other channels — that is a
pulse-server concern, but cron should:

- Keep request timeout at 30 s so a stuck channel can't pin a worker.
- Not loop-retry beyond `MAX_RETRY_ATTEMPTS=3`.

## 5. Rate limits

Slack webhook rate limits live in pulse-server. Cron simply does not
re-fire an alert before its interval elapses; per-alert interval is
the de-facto rate limit at this layer.

## 6. Tests

Same WebClient mocks as email path — assertion is on URL + retry
behavior, not channel semantics.

## 7. Observability

Log `alertId`, HTTP status. Channel-specific metrics live in
pulse-server.

## 8. Cross-links

- [cron-manager](../core/cron-manager.md)
- Sibling channels: [email](./email.md), [webhook](./webhook.md)

## 9. Open items

- None at cron layer.
