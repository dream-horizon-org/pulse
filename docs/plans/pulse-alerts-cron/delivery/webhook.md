# delivery / webhook (delegated)

Parent: [pulse-alerts-cron](../index.md) ·
Brief: [component](../../../components/pulse-alerts-cron.md)

## 1. Purpose

Generic outbound webhook channel; like email and Slack, the actual
HTTP POST to the customer's endpoint is performed by **pulse-server**.
Cron only schedules the evaluation.

## 2. Trigger

Cron tick → POST
`{pulseServerUrl}/v1/alert/evaluateAndTriggerAlert?alertId={id}`. If
webhook is configured, pulse-server signs + posts the payload.

## 3. Cron-side responsibilities

- Stable cadence (see [cron-manager](../core/cron-manager.md)).
- 5xx retry with exponential backoff; abandon after 3 attempts.

## 4. Customer-endpoint timeouts

The 30 s `REQUEST_TIMEOUT_MS` is the cron → pulse-server timeout.
Customer-endpoint timeout is a pulse-server setting (typically shorter)
so a slow customer cannot pin cron retries.

## 5. Idempotency

If cron retries pulse-server after a partial 5xx, pulse-server should
dedupe by `(alertId, evaluationWindow)` to avoid duplicate customer
deliveries.

## 6. Security

Webhook signing secret + HMAC live in pulse-server. Cron never sees
customer endpoints.

## 7. Tests

WebClient mock — same retry assertions as email/slack.

## 8. Cross-links

- [cron-manager](../core/cron-manager.md)
- Sibling channels: [email](./email.md), [slack](./slack.md)

## 9. Open items

- Optional: cron-side metric for cumulative retry budget burn per
  alert, to detect a stuck customer endpoint upstream of pulse-server
  signals.
