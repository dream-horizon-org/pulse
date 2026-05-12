# Compose · Env Vars

Env vars consumed by `deploy/docker-compose.yml` (defaults shown via `${VAR:-default}`).

Brief: [../../../components/deploy.md](../../../components/deploy.md) · Peers: [services](./services.md).

## MySQL

| Var | Default |
|---|---|
| `MYSQL_ROOT_PASSWORD` | `pulse_root_password` |
| `MYSQL_DATABASE` | `pulse_db` |
| `MYSQL_USER` | `pulse_user` |
| `MYSQL_PASSWORD` | `pulse_password` |

Also consumed by `backend/server` via `MYSQL_WRITER_HOST`, `MYSQL_READER_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_WRITER_MAX_POOL_SIZE`, `MYSQL_READER_MAX_POOL_SIZE` (see `backend/server/src/main/resources/conf/mysql-default.conf`).

## ClickHouse

| Var | Notes |
|---|---|
| `CLICKHOUSE_ENDPOINT` | e.g. `tcp://clickhouse:9000` (exporter) |
| `CLICKHOUSE_DATABASE` | `otel` |
| `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD` | per-env |

Consumed by both the OTel Collector exporter and `backend/server` ClickHouse client.

## Google OAuth

| Var | Effect |
|---|---|
| `GOOGLE_OAUTH_ENABLED` | `false` switches to mock users |
| `GOOGLE_CLIENT_ID` | OAuth client id |
| `GOOGLE_CLIENT_SECRET` | OAuth client secret |
| `GOOGLE_API_KEY` | Needed by `pulse_ai` (Gemini). Without it, `quickstart.sh` skips the AI service. |

## Kafka

| Var | Notes |
|---|---|
| `KAFKA_BROKERS` | e.g. `kafka:9092` |
| `KAFKA_TOPIC_REPLAY`, `KAFKA_TOPIC_HEATMAP` | topic names |

## AWS (for S3 sinks, replay/heatmap)

| Var | Notes |
|---|---|
| `AWS_REGION` | e.g. `ap-south-1` (matches `vector.yaml`) |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | for S3 sinks |
| `S3_BUCKET_REPLAY`, `S3_BUCKET_HEATMAP` | per-service buckets |

## Validation

`scripts/validate-env-variables.sh` fails fast if required vars are missing. `scripts/common.sh::load_env` sources `deploy/.env` if present.

## Rebuild recipe

1. Copy `.env.example` → `.env` and fill in.
2. Run `scripts/validate-env-variables.sh`.
3. Reference every var in `docker-compose.yml` with a safe default for dev.
