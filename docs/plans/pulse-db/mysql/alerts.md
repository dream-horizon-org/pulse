# MySQL — alerts & notifications

## Purpose

Stores alert definitions, scopes, evaluation history, derived incidents, and the notification routing graph (channels, templates, mappings, logs, suppression).

## Source

`backend/db/{dev,prod}/mysql/mysql-init.sql` — sections starting at `severity`, `alerts`, `alert_scope`, `alert_metrics`, `notification_channels`, `notification_templates`, `channel_event_mapping`, `notification_logs`, `email_suppression_list`, `incidents`, `usage_limit_notifications`, `cron_jobs_history`.

## Tables

- `severity` — enum-like severity rows referenced by alerts.
- `alerts` — alert config (project, metric, threshold, comparator, evaluation window, severity, created_by user_id).
- `alert_scope` + `scope_types` — filter dimensions (e.g. platform, app version, screen).
- `alert_metrics` — metric catalog usable in alert rules.
- `alert_evaluation_history` — every evaluation result (driven by `pulse-alerts-cron`).
- `incidents` — open / resolved incident rows derived from sustained alert breaches.
- `notification_channels` (current) / `notification_channels_old` (legacy, retained for migration) — destinations (email, webhook, Slack, etc.).
- `notification_templates` — per-channel template bodies.
- `channel_event_mapping` — which events fan out to which channels.
- `notification_logs` — dispatch audit trail.
- `email_suppression_list` — bounce / unsubscribe list.
- `usage_limit_notifications` — emits when project_usage_limits crossed.
- `cron_jobs_history` — generic cron run log (alerts cron, RCA jobs, athena jobs).

## Inputs

- `pulse-ui` alert form → `pulse-server` REST → writes `alerts` + `alert_scope`.
- `pulse-alerts-cron` `CronManager` schedules per-alert evaluation via Vert.x timers, calls ClickHouse, writes `alert_evaluation_history`, opens/closes `incidents`, dispatches via `notification_*`.

## Outputs

- Webhook / email dispatches consumed by external systems.
- Dashboard reads `alert_evaluation_history` for charts and `incidents` for the status feed.

## Operational notes

- ClickHouse query construction for alerts lives in `AlertsService` (cron module) — uses materialized columns (`ProjectId`, `Platform`, `AppVersion`, `PulseType`).
- Adding an alert metric touches MySQL schema → backend DAO/service → CH query → cron → UI alert form → `pulse_ai/` registry (see `CLAUDE.md`).

## Failure modes

- A schema-only addition to `alert_metrics` without backend changes will fail at runtime when the cron tries to map the metric to a CH query.
- Email dispatch loops when `email_suppression_list` is not honoured by the channel implementation.

## Related code

- `backend/pulse-alerts-cron/src/main/java/.../service/AlertsService.java`, `CronManager.java`.
- `pulse-ui/src/screens/Alerts/*`.

## Open questions

- `notification_channels_old` retention: no automated drop — manual when migration confirmed.
