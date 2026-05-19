---
name: add-alert-metric
description: Cross-cutting workflow for adding a new alert metric spanning DB, backend, cron, frontend, and AI agent. Only invoke explicitly when adding a new metric type.
disable-model-invocation: true
---

# Add Alert Metric (Cross-Cutting)

This is a cross-cutting change — a new alert metric touches 5 layers.

## Workflow

```
- [ ] Step 1: MySQL migration — add row to alert_metrics (Liquibase)
- [ ] Step 2: Backend — add to MetricType enum and ClickHouse query builder
- [ ] Step 3: Alerts cron — verify evaluation handles new metric
- [ ] Step 4: Frontend — add metric option to alert form
- [ ] Step 5: AI agent — add to metrics registry
- [ ] Step 6: Test end-to-end
```

## Step 1: MySQL Migration (Liquibase)

Add a new changeset under `backend/db/migrations/mysql/`, e.g. `V0002__add_my_new_metric.sql`:

```sql
--liquibase formatted sql

--changeset db-migrations:V0002__add_my_new_metric runOnChange:false failOnError:true
--comment Register MY_NEW_METRIC for alerts UI and cron evaluation

INSERT INTO alert_metrics (scope_type_id, metric_name, display_name, 
description)
VALUES (1, 'MY_NEW_METRIC', 'My New Metric', 'Description of what this 
metric measures');
```

Register it in `backend/db/migrations/mysql/changelog-root.xml` with a new `<include>`.

**Fresh local installs:** baseline `V0001__baseline.sql` is only for greenfield DBs. Existing envs get the new changeset via `liquibase:update` (local: `pulse-db-migrate`; prod: Jenkins deploy or `db-migrations-sync`).

Do **not** edit `V0001__baseline.sql` after it has been applied anywhere unless you follow the Liquibase checksum / clearCheckSums process.

## Step 2: Backend

1. Add to the metric enum/constants in the backend service layer
2. Update `ClickhouseMetricService` to build the correct ClickHouse SQL for this metric
3. Add the ClickHouse query function (e.g., percentile, count, rate calculation)

## Step 3: Alerts Cron

Verify `AlertEvaluationService` can evaluate the new metric:
- Check threshold comparison logic handles the metric's value type
- Ensure notification formatting includes the metric name

## Step 4: Frontend

1. The metric should appear automatically if it's fetched from the API
2. If custom UI is needed, update the alert form in `pulse-ui/src/screens/AlertFormWizard/`
3. Add display formatting if the metric has special units

## Step 5: AI Agent

**Note:** The AI agent currently has a flat structure (`pulse_ai/agent.py`) with no registries. When registries are added, create or update the metrics registry. For now, update the root agent's instruction in `pulse_ai/agent.py` to include knowledge of the new metric, or add a registry file at the `pulse_ai/` root when the first metric is added.

## Step 6: End-to-End Test

1. Create an alert with the new metric via the UI
2. Verify the cron evaluates it correctly
3. Ask the AI agent about the metric in natural language
