# Components — Manifest

This is the index of every Pulse component brief. Each brief is a one-page overview; deeper context lives in `../plans/<component>/`.

**Reading order:** `../architecture.md` → this manifest → `<component>.md` brief → `../plans/<component>/index.md` → specific handbook files.

**Routing rule for agents:** load only the briefs you need, then drill into plans on demand. Never load the whole tree.

---

## Backend services

| Component | Path | Purpose | Brief | Plan |
|---|---|---|---|---|
| backend-server | `backend/server/` | Core REST API + multi-tenant query | [brief](./backend-server.md) | [plan](../plans/backend-server/index.md) |
| pulse-alerts-cron | `backend/pulse-alerts-cron/` | Scheduled alert evaluator | [brief](./pulse-alerts-cron.md) | [plan](../plans/pulse-alerts-cron/index.md) |
| session-capture-service | `backend/session-capture-service/` | WS ingestor for rrweb session events | [brief](./session-capture-service.md) | [plan](../plans/session-capture-service/index.md) |
| session-replay-ingestion | `backend/session-replay-ingestion/` | Kafka→S3 replay writer | [brief](./session-replay-ingestion.md) | [plan](../plans/session-replay-ingestion/index.md) |
| heatmap-screenshot-ingestion | `backend/heatmap-screenshot-ingestion/` | Kafka→S3+Redis screenshot pipeline | [brief](./heatmap-screenshot-ingestion.md) | [plan](../plans/heatmap-screenshot-ingestion/index.md) |
| spark-jobs | `backend/spark/` | Batch aggregations | [brief](./spark-jobs.md) | [plan](../plans/spark-jobs/index.md) |
| pulse-db | `backend/db/` | SQL migrations (ClickHouse + MySQL) | [brief](./pulse-db.md) | [plan](../plans/pulse-db/index.md) |
| pulse-ingestion | `backend/ingestion/` | OTel Collector + Athena DDL | [brief](./pulse-ingestion.md) | [plan](../plans/pulse-ingestion/index.md) |

## SDKs

| Component | Path | Purpose | Brief | Plan |
|---|---|---|---|---|
| pulse-web-otel | `pulse-web-otel/` | Browser SDK | [brief](./pulse-web-otel.md) | [plan](../plans/pulse-web-otel/index.md) |
| pulse-android-otel | `pulse-android-otel/` | Android SDK | [brief](./pulse-android-otel.md) | [plan](../plans/pulse-android-otel/index.md) |
| pulse-ios-otel | `pulse-ios-otel/` | iOS SDK | [brief](./pulse-ios-otel.md) | [plan](../plans/pulse-ios-otel/index.md) |
| pulse-react-native-otel | `pulse-react-native-otel/` | React Native SDK | [brief](./pulse-react-native-otel.md) | [plan](../plans/pulse-react-native-otel/index.md) |

## Frontends & adapters

| Component | Path | Purpose | Brief | Plan |
|---|---|---|---|---|
| pulse-ui | `pulse-ui/` | React + Mantine dashboard | [brief](./pulse-ui.md) | [plan](../plans/pulse-ui/index.md) |
| pulse-mcp | `pulse-mcp/` | MCP server exposing read-only tools | [brief](./pulse-mcp.md) | [plan](../plans/pulse-mcp/index.md) |
| pulse-ai | `pulse_ai/` | Google ADK + Gemini agent | [brief](./pulse-ai.md) | [plan](../plans/pulse-ai/index.md) |

## Infra & ops

| Component | Path | Purpose | Brief | Plan |
|---|---|---|---|---|
| vector | `vector/` | OTLP → S3 / ClickHouse routing | [brief](./vector.md) | [plan](../plans/vector/index.md) |
| deploy | `deploy/` | Docker Compose + scripts | [brief](./deploy.md) | [plan](../plans/deploy/index.md) |

---

If you only have time for one file: read `../architecture.md`. Everything else is reachable via the table above.
