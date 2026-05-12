# Pulse — Platform Architecture

Pulse is a real-time mobile + web observability platform built on OpenTelemetry. SDKs on mobile, web, and React Native emit OTLP signals to a Collector, which fans out to ClickHouse (telemetry) and S3 (raw events / session replay / screenshots). A React dashboard plus a Java API let teams investigate crashes, sessions, network calls, screens, web vitals, funnels, heatmaps, and alerts.

This document is the **top of the docs router**. Read this first, then drill via `docs/components/MANIFEST.md` → `docs/components/<comp>.md` → `docs/plans/<comp>/index.md` → specific sub-component handbook files.

---

## High-level diagram

```
                +---------------------+    +---------------------+
   Mobile SDKs  | pulse-android-otel  |    | pulse-ios-otel      |
   Web SDK      | pulse-web-otel      |    | pulse-react-native- |
                |                     |    |     otel            |
                +----------+----------+    +----------+----------+
                           |                          |
                           |    OTLP gRPC :4317        |
                           |    OTLP HTTP :4318        |
                           v                          v
                +-----------------------------------------------+
                |   OTel Collector  (backend/ingestion/)        |
                |   + Vector  (vector/)  — custom events        |
                +---+----------------------+--------------------+
                    |                      |
        traces/logs/|metrics               | parquet (custom events)
                    v                      v
              +-----------+           +---------+
              | ClickHouse|           |   S3    | ---> Athena
              |  (otel DB)|           +---------+
              +-----+-----+
                    ^
        +-----------+-----------+        +-------------------+
        | Pulse Server (Vert.x) | <----- | Pulse Alerts Cron |
        | REST API :8080        |        | :4000             |
        +-----+-------------+---+        +---------+---------+
              |             |                      |
              |             | MySQL (writer/reader)|
              |             v                      v
              |       +-----------+         (email / slack /
              |       |   MySQL   |          webhook delivery)
              |       +-----------+
              v
        +-------------+         +-------------+        +---------+
        | Pulse UI    |         |  Pulse AI   |        |Pulse MCP|
        | React :3000 |         |  ADK :8000  |        | stdio   |
        +-------------+         +-------------+        +---------+

  Session-replay path
  -------------------
   pulse-web-otel ---> session-capture-service (Rust/WS) ---> Kafka ---> session-replay-ingestion ---> S3
                                                                  \---> heatmap-screenshot-ingestion ---> S3 + Redis
                                                                  \---> spark-jobs (batch rollups) ---> ClickHouse
```

---

## Component map

Briefs live at `docs/components/<name>.md`; deep handbooks at `docs/plans/<name>/`.

| Component | Path | Role |
|---|---|---|
| backend-server | `backend/server/` | Core REST API, multi-tenant query and config |
| pulse-alerts-cron | `backend/pulse-alerts-cron/` | Scheduled alert evaluation + notification |
| session-capture-service | `backend/session-capture-service/` | WebSocket ingestor for rrweb session events → Kafka |
| session-replay-ingestion | `backend/session-replay-ingestion/` | Kafka → S3 batched writer for replay payloads |
| heatmap-screenshot-ingestion | `backend/heatmap-screenshot-ingestion/` | Kafka → S3 + Redis for click heatmap screenshots |
| spark-jobs | `backend/spark/` | Batch aggregations (heatmap rollups, funnels, etc.) |
| pulse-db | `backend/db/` | SQL migrations for ClickHouse + MySQL |
| pulse-ingestion | `backend/ingestion/` | OTel Collector + Athena DDL configs |
| pulse-ui | `pulse-ui/` | React + Mantine dashboard |
| pulse-web-otel | `pulse-web-otel/` | Browser SDK |
| pulse-android-otel | `pulse-android-otel/` | Android SDK |
| pulse-ios-otel | `pulse-ios-otel/` | iOS SDK |
| pulse-react-native-otel | `pulse-react-native-otel/` | React Native SDK |
| pulse-mcp | `pulse-mcp/` | MCP server exposing read-only Pulse tools |
| pulse-ai | `pulse_ai/` | Google ADK + Gemini agent platform |
| vector | `vector/` | OTLP → S3 / ClickHouse routing for custom events |
| deploy | `deploy/` | Docker Compose orchestration + scripts |

---

## Data flow

### Telemetry path (default)
1. SDK records a span/log/metric, tags it with `pulse.type` (e.g. `screen_session`, `device.crash`, `app.click`, `http`, `web_vital`).
2. SDK exporter posts OTLP to the Collector (gRPC `:4317` or HTTP `:4318`).
3. Collector batches and writes to ClickHouse `otel` database — tables `otel_traces`, `otel_logs`, `otel_metrics_*`, and projection table `stack_trace_events`.
4. `pulse-ui` issues ad-hoc queries via `backend-server` (`/v1/query`, `/v1/screens/...`, etc.) which compose ClickHouse SQL using materialized columns (`ProjectId`, `PulseType`, `Platform`, `AppVersion`, `SessionId`).
5. No precomputed rollups for screens/network/sessions — every page hit = real-time ClickHouse query (see `docs/plans/pulse-ui/screens/screen-list.md`).

### Custom-events path
1. SDK emits an OTLP-shaped custom event; Vector tags and routes it.
2. Vector writes Parquet files to S3.
3. Athena queries those Parquet files for ad-hoc analytics (see `docs/plans/pulse-ingestion/collector/athena-ddl.md`).

### Session replay & heatmaps
1. `pulse-web-otel` opens a WebSocket to `session-capture-service` and streams compressed rrweb events.
2. `session-capture-service` produces to Kafka.
3. `session-replay-ingestion` consumes → batches → writes payloads to S3 keyed by `projectId/sessionId`.
4. `heatmap-screenshot-ingestion` consumes screenshot frames, caches metadata in Redis, persists images to S3.
5. `spark-jobs` aggregates clicks per (screen, viewport) → ClickHouse `interaction_heatmaps_daily`.

### Alerting
1. `pulse-alerts-cron` reads alert configs from MySQL.
2. On schedule, it issues a ClickHouse query (URL composed by `AlertsService`).
3. Comparison vs threshold → emits notification via email / Slack / webhook channel.

---

## Storage split

| Store | What lives there |
|---|---|
| ClickHouse `otel` | All telemetry: traces, logs, metrics, screen sessions, network, crashes, interactions, heatmap rollups |
| MySQL | Control plane: tenants, projects, organizations, members, API keys, JWT/OAuth state, alert configs, critical-interaction configs, event definitions, tiers, usage limits, sampling configs, personal tokens |
| S3 | Raw session-replay payloads, heatmap screenshots, custom-events Parquet |
| Redis | Hot path cache for `heatmap-screenshot-ingestion` (de-duplication, batch flush) |
| Kafka | In-flight bus for replay + screenshot pipelines |

Rule of thumb: **"what did the SDK send?" → ClickHouse**; **"who owns it / how is it configured?" → MySQL**.

---

## Auth

- **Production:** Google OAuth 2.0 → server-issued JWT pair (access 24h, refresh 30d). UI calls go through `pulse-ui/src/helpers/makeRequest.ts` which transparently refreshes on `401`.
- **Dev mode** (`GOOGLE_OAUTH_ENABLED=false`): mock users `mock-user-1`, `mock-user-2`; project `default-project`; API key `default-project_devkey01`.
- SDK ingestion authenticates via per-project ingestion key embedded in resource attributes — not via JWT.

Details: `docs/plans/backend-server/core/auth.md`.

---

## Multi-tenancy

- Every signal carries `ResourceAttributes['project.id']`, materialized into the ClickHouse column `ProjectId`.
- Every backend query must filter by `ProjectId`. The pattern is enforced by `backend-server` controllers (project ID is path-bound via `/v1/projects/{projectId}/...`).
- ClickHouse row policies provide a second isolation layer per project.
- API-key checks, JWT claims, and DB row policies all converge on the same `projectId`.

---

## Cross-cutting SDK contract

Every signal from every SDK must carry:
- `platform` = `web` | `android` | `ios` | `react-native`
- `pulse.type` = one of the canonical values (`session.start`, `session.end`, `device.crash`, `non_fatal`, `http`, `app.click`, `web_vital`, `screen_load`, `screen_interactive`, `screen_session`)
- `project.id`, `session.id`, `installation.id`, `app.build_name`

Parity is the contract: a feature shipped on Android must use the same `pulse.type` and attribute names on iOS, RN, and web. See `docs/plans/pulse-web-otel/core/semconv.md` for the canonical table and the mobile SDK plan files for parity notes.

---

## Build & run

For per-component build commands, see each `docs/components/<comp>.md` brief. For full-stack local boot:

```bash
cd deploy && ./scripts/quickstart.sh         # build + start everything
cd deploy && ./scripts/start.sh -d           # background mode
cd deploy && ./scripts/logs.sh <service>     # tail any service
cd deploy && ./scripts/stop.sh [-v]          # stop; -v wipes volumes
```

---

## How to read the rest of `docs/`

1. **Need a component-by-component map?** → `docs/components/MANIFEST.md`.
2. **Need to make a change in component X?** → `docs/components/X.md` brief → `docs/plans/X/index.md` → only the sub-files you need.
3. **Need to recreate component X from scratch?** → `docs/plans/X/` is sufficient; each handbook file has a "Rebuild recipe" section.
4. **Need cross-cutting context (auth, semconv, multi-tenancy)?** → see this file, and the linked files above.

Routing rule for agents: never load all of `docs/plans/`. Load only the leaves required for the current task.
