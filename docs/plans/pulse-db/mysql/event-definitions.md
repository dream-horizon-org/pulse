# MySQL — events, interactions, funnels, SDK configs

## Purpose

Pulse-specific product analytics primitives: critical interactions, user-defined events + attributes, funnels and journeys, and the SDK config blob clients fetch on startup.

## Source

`backend/db/{dev,prod}/mysql/mysql-init.sql` — sections `interaction`, `suggested_interaction`, `pulse_sdk_configs`, `event_definitions`, `event_attribute_definitions`, `funnel`, `journey`, `funnel_journey_tag`, `analytics_jobs`, `symbol_files`, `athena_job`, `rca_report_cache`, `rca_report_jobs`.

## Tables

- `interaction` — critical-interaction configs (user journey definitions: start/end markers, step events, project-scoped). Dev seed populates 5 web flows + 2 legacy mobile samples.
- `suggested_interaction` — AI-mined interaction patterns (events + props derived from telemetry).
- `pulse_sdk_configs` — JSON config returned by `/v1/interaction-configs/` and used by SDKs. Default template (sampling, signals, interaction URLs, feature toggles per SDK) is documented inline in the init SQL.
- `event_definitions` + `event_attribute_definitions` — user-defined custom-event catalog (name, type, attributes, validation).
- `funnel`, `journey` — analytics flow definitions (ordered steps). Joined to `funnel_journey_tag` for tagging.
- `analytics_jobs` — orchestration rows for funnel/journey computation (Spark vs ClickHouse routing per `ANALYTICS_COMPUTE_ENGINE`).
- `symbol_files` — uploaded de-obfuscation artifacts (composite PK includes `project_id`).
- `athena_job` — Athena table provisioning state per project (drives `pulse_athena_db.otel_data_<project_id>` creation).
- `rca_report_cache`, `rca_report_jobs` — Root Cause Analysis cache + job queue (gated by `ROOT_CAUSE_ENABLED`).

## Inputs

- `pulse-ui` interactions / events / funnels / journeys screens.
- SDK config CRUD APIs in `pulse-server`.
- AI agent suggests interactions and writes to `suggested_interaction`.

## Outputs

- SDKs fetch `pulse_sdk_configs` at boot (`INTERACTION_CONFIG_URL`).
- `analytics_jobs` → ClickHouse `otel.funnel_results` / `otel.journey_results`.
- `athena_job` → external Athena DDL run (template at `backend/ingestion/athena-otel-tables.sql`).

## Operational notes

- The SDK config template carries collector URLs (`logsCollectorUrl`, `metricCollectorUrl`, `spanCollectorUrl`, interaction collector + config URL) which default to `http://10.0.2.2:4318/...` for Android emulator; production overrides via env.
- Feature gating per-SDK is by `features[].sdks` (e.g. `pulse_android_java`, `pulse_android_rn`, `pulse_ios_swift`, `pulse_ios_rn`, web-sdk equivalents).

## Failure modes

- `pulse_sdk_configs` rows with stale collector URLs → silent SDK drop traffic. Always sync after `.env` change.
- `analytics_jobs` stuck → check `cron_jobs_history` and Spark / CH executor logs.

## Related code

- `backend/server/.../service/SdkConfigService.java`, `InteractionService.java`, `EventDefinitionService.java`, `FunnelService.java`, `JourneyService.java`.
- `backend/server/.../service/AthenaJobService.java` (provisioning).

## Open questions

- Interaction-config schema is enforced in code, not as a JSON-Schema constraint in MySQL; drift risk between SDK readers.
