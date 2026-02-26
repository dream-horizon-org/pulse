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
clickhouse-client --host="$CH_HOST" --user="$CH_USER" --password="$CH_PASSWORD" --database="$CH_DATABASE" --multiquery < /init/clickhouse-otel-schema.sql
echo "✓ ClickHouse tables created successfully!"

