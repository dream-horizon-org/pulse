---
paths:
  - "backend/pulse-alerts-cron/**/*.java"
---

# Alerts Cron Conventions

## Purpose

Periodically evaluates alert conditions by querying ClickHouse and notifying via Slack/webhooks.

## Package Structure

```
org.dreamhorizon.pulsealertscron/
├── rest/           # REST controllers
├── services/       # Business logic
├── dao/            # Data access
├── dto/
│   ├── request/
│   └── response/
├── models/         # Domain models
├── error/          # Error codes
├── config/         # Configuration
├── constant/       # Shared constants
├── guice/          # DI setup
├── module/         # Guice modules
├── util/           # Utilities
└── verticle/       # Vert.x verticles
```

## Alert Metric Scopes

`INTERACTION` · `SCREEN` · `NETWORK` · `APP_VITALS`

## Retry Policy

- `MAX_RETRY_ATTEMPTS = 3`
- `INITIAL_RETRY_DELAY_MS = 1000`
- Exponential backoff

## Key Behaviour

- `CronManager` schedules alert evaluation via Vert.x timers
- `AlertsService` fetches alerts from pulse-server, builds ClickHouse query URLs
- Port: **4000**, health: `/healthcheck`

## Conventions

Same Java conventions as `backend/server/` — RxJava3, Guice DI, ServiceError enum, Lombok, Google Checkstyle.
