#!/usr/bin/env bash
# Run the INTERACTION_EVENT_PATTERN Spark job locally against Docker MySQL + ClickHouse (no EMR).
#
# Prerequisites:
#   - Apache Spark 3.5.x (Scala 2.12) on PATH or SPARK_HOME set
#   - Java 17
#   - MySQL (e.g. pulse-mysql) published on localhost:3307, ClickHouse on localhost:8123
#   - A row in pulse_db.interaction_event_pattern_request (id = REFERENCE_ID)
#
# Usage:
#   REFERENCE_ID=2 ./deploy/scripts/run-event-pattern-spark-local.sh
#
# Optional:
#   SPARK_JOB_ID=2     — if set, updates pulse_db.spark_jobs for that id (RUNNING/SUCCEEDED/FAILED)
#   MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307
#   CLICKHOUSE_HOST=127.0.0.1 CLICKHOUSE_PORT=8123
#   Loads deploy/.env when present (MYSQL_PASSWORD, OTEL_CLICKHOUSE_*, etc.)
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SPARK_DIR="${REPO_ROOT}/backend/spark"
JAR="${SPARK_DIR}/target/pulse-spark-jobs-1.0-SNAPSHOT.jar"

if [[ -z "${REFERENCE_ID:-}" ]]; then
  echo "ERROR: Set REFERENCE_ID to interaction_event_pattern_request.id (e.g. REFERENCE_ID=2)" >&2
  exit 1
fi

if [[ -f "${REPO_ROOT}/deploy/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${REPO_ROOT}/deploy/.env"
  set +a
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_DB="${MYSQL_DATABASE:-pulse_db}"
MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"

CLICKHOUSE_HOST="${CLICKHOUSE_HOST:-127.0.0.1}"
CLICKHOUSE_PORT="${CLICKHOUSE_PORT:-8123}"
CLICKHOUSE_DB="${OTEL_CLICKHOUSE_DATABASE:-otel}"
CLICKHOUSE_USER="${OTEL_CLICKHOUSE_USER:-pulse_user}"
CLICKHOUSE_PASSWORD="${OTEL_CLICKHOUSE_PASSWORD:-pulse_password}"

S3_PREFIX="${S3_BUCKET_PREFIX:-pulse-otel-}"

if [[ ! -f "$JAR" ]]; then
  echo "Building Spark fat JAR..."
  (cd "$SPARK_DIR" && mvn -q package -DskipTests)
fi

if [[ -n "${SPARK_HOME:-}" ]]; then
  SPARK_SUBMIT="${SPARK_HOME}/bin/spark-submit"
else
  SPARK_SUBMIT="$(command -v spark-submit || true)"
fi

if [[ -z "$SPARK_SUBMIT" || ! -x "$SPARK_SUBMIT" ]]; then
  echo "ERROR: spark-submit not found. Install Spark 3.5.x (Scala 2.12) or set SPARK_HOME." >&2
  echo "  macOS (Homebrew): brew install apache-spark" >&2
  exit 1
fi

# Java 17 + Spark 3.5 — required module opens for the driver
JAVA_OPENS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED"

APP_ARGS=(
  "--job_type" "INTERACTION_EVENT_PATTERN"
  "--reference_id" "${REFERENCE_ID}"
  "--mysql_host" "${MYSQL_HOST}"
  "--mysql_port" "${MYSQL_PORT}"
  "--mysql_db" "${MYSQL_DB}"
  "--mysql_user" "${MYSQL_USER}"
  "--mysql_password" "${MYSQL_PASSWORD}"
  "--clickhouse_host" "${CLICKHOUSE_HOST}"
  "--clickhouse_port" "${CLICKHOUSE_PORT}"
  "--clickhouse_db" "${CLICKHOUSE_DB}"
  "--clickhouse_user" "${CLICKHOUSE_USER}"
  "--clickhouse_password" "${CLICKHOUSE_PASSWORD}"
  "--s3_bucket_prefix" "${S3_PREFIX}"
)

if [[ -n "${SPARK_JOB_ID:-}" ]]; then
  APP_ARGS+=("--spark_job_id" "${SPARK_JOB_ID}")
fi

echo "Using JAR: $JAR"
echo "reference_id=${REFERENCE_ID} mysql=${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB} clickhouse=${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/${CLICKHOUSE_DB}"

exec "$SPARK_SUBMIT" \
  --master 'local[*]' \
  --conf "spark.driver.extraJavaOptions=${JAVA_OPENS}" \
  --class org.dreamhorizon.pulsespark.SparkJobRunner \
  "$JAR" \
  "${APP_ARGS[@]}"
