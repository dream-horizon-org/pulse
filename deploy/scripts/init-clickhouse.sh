#!/bin/bash
# ClickHouse Table Initialization Script
# This script ensures ClickHouse tables are created even if the container is recreated

set -e

CH_HOST="${CLICKHOUSE_HOST:-clickhouse}"
CH_USER="${CLICKHOUSE_USER:-pulse_user}"
CH_PASSWORD="${CLICKHOUSE_PASSWORD:-pulse_password}"
CH_DATABASE="${CLICKHOUSE_DB:-otel}"

MAX_RETRIES=60
RETRY_INTERVAL=3

echo "Waiting for ClickHouse to be ready..."
sleep 5

retries=0
until clickhouse-client --host="$CH_HOST" --user="$CH_USER" --password="$CH_PASSWORD" --query="SELECT 1" > /dev/null 2>&1; do
    retries=$((retries + 1))
    if [ "$retries" -ge "$MAX_RETRIES" ]; then
        echo "ERROR: ClickHouse did not become ready after $((MAX_RETRIES * RETRY_INTERVAL))s. Giving up."
        exit 1
    fi
    echo "Waiting for ClickHouse... (attempt $retries/$MAX_RETRIES)"
    sleep "$RETRY_INTERVAL"
done

echo "Creating ClickHouse tables..."
# Order matters: MVs and views reference other tables. Do not use plain `ls | sort`
# (alphabetical order breaks e.g. interaction_heatmaps_daily before otel_logs,
# project_monthly_usage before stack_trace_events, event_catalog MVs before otel_logs).
CH_SQL_FILES=(
  "otel.otel_logs.sql"
  "otel.otel_traces.sql"
  "otel.stack_trace_events.sql"
  "otel.session_replay_events.sql"
  "otel.root_cause_cache.sql"
  "otel.screen_root_cause_cache.sql"
  "otel.funnel_results.sql"
  "otel.journey_results.sql"
  "otel.project_monthly_usage.sql"
  "otel.otel_metrics_sum.sql"
  "otel.otel_metrics_histogram.sql"
  "otel.otel_metrics_exp_histogram.sql"
  "otel.otel_metrics_summary.sql"
  "otel.interaction_heatmaps_daily.sql"
  "otel.session_summary.sql"
  "otel.event_catalog_entries.sql"
)
for sql_basename in "${CH_SQL_FILES[@]}"; do
    sql_file="/init/clickhouse/${sql_basename}"
    if [ ! -r "$sql_file" ]; then
        echo "ERROR: Expected SQL file missing or unreadable: $sql_file"
        exit 1
    fi
    echo "Executing $sql_basename..."
    clickhouse-client --host="$CH_HOST" --user="$CH_USER" --password="$CH_PASSWORD" --database="$CH_DATABASE" --multiquery < "$sql_file"
done
echo "✓ ClickHouse tables created successfully!"