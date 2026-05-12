# pulse-alerts-cron — plan

Component brief: [`docs/components/pulse-alerts-cron.md`](../../components/pulse-alerts-cron.md).

## Scope

Keep alert evaluation honest under load: stable cadence per interval,
no missed ticks, no thundering-herd against pulse-server, and explicit
retry/backoff on transient REST failures. Also run periodic sync jobs
(usage credits, API keys, usage-limit emails) and trigger batch
analytics endpoints on the configured cron time.

## Architecture sketch

```
[pulse-server] -- GET /alerts ---------- [DataSyncService]
                                              |
                                              v
                                       [CronManager]
                                  (interval -> CopyOnWriteArrayList<CronTask>)
                                  vertx.setPeriodic(interval*1000, ...)
                                              |
                                              v
                       POST /v1/alert/evaluateAndTriggerAlert?alertId={id}
                                       [pulse-server]
                                              |
                                              v
                        ClickHouse query + threshold + notify (email/Slack/webhook)

[BatchSchedulerService] -- cron --> POST /batch{funnels,journeys,events}
[PeriodicSyncService]   -- timer --> POST /usageCredits, /apiKeys, /usageLimitEmails
```

## Sub-components

Core:

- [core/cron-manager.md](./core/cron-manager.md) — `CronManager`,
  interval grouping, Vert.x timers, retry.
- [core/alerts-service.md](./core/alerts-service.md) — fetch + decorate
  alert configs.
- [core/clickhouse-evaluation.md](./core/clickhouse-evaluation.md) —
  evaluation is delegated to pulse-server; this doc explains the
  contract and what would change if we inlined evaluation here.

Delivery (notifications — all owned by pulse-server today):

- [delivery/email.md](./delivery/email.md)
- [delivery/slack.md](./delivery/slack.md)
- [delivery/webhook.md](./delivery/webhook.md)

## Cross-links

- Triggers: [`spark-jobs`](../spark-jobs/index.md) via batch endpoints.
- Mirrors: `backend/server/` conventions (Vert.x, Guice, RxJava3,
  ServiceError, Lombok, Google Checkstyle 140-char).

## Risks

- Single-process timers: if the pod dies mid-tick, that interval's
  fan-out is lost until restart. Not HA today.
- `CopyOnWriteArrayList` writes are O(n); fine at current scale, watch
  if alert count grows.
- Retry storms: `MAX_RETRY_ATTEMPTS=3` with 1s initial backoff and
  `REQUEST_TIMEOUT_MS=30000` — verify under pulse-server brownout.
