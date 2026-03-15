#!/bin/bash
# ============================================================================
# Demo Data Seed Entrypoint
# ============================================================================
# Runs inside a Docker container to seed Pulse with realistic e-commerce
# telemetry data. Waits for MySQL and ClickHouse to be ready, then runs
# the Python seed script.
#
# Enable by adding to .env:   COMPOSE_PROFILES=demo
# ============================================================================

set -e

echo "========================================="
echo "  Pulse Demo Data Seeder"
echo "========================================="
echo ""

# Install mysql-client for the seed script and health checks
echo "Installing dependencies..."
apt-get update -qq && apt-get install -y -qq default-mysql-client curl > /dev/null 2>&1
echo "  Dependencies installed"

CH_HOST="${CH_HOST:-clickhouse}"
CH_PORT="${CH_PORT:-8123}"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
MYSQL_DB="${MYSQL_DB:-pulse_db}"

MAX_RETRIES=30

# ── Wait for ClickHouse ──────────────────────────────────────────────────────
echo ""
echo "Waiting for ClickHouse at ${CH_HOST}:${CH_PORT}..."
retries=0
until curl -sf "http://${CH_HOST}:${CH_PORT}/ping" > /dev/null 2>&1; do
    retries=$((retries + 1))
    if [ $retries -ge $MAX_RETRIES ]; then
        echo "  ERROR: ClickHouse not ready after ${MAX_RETRIES} attempts"
        exit 1
    fi
    sleep 2
done
echo "  ClickHouse is ready"

# ── Wait for MySQL ───────────────────────────────────────────────────────────
echo "Waiting for MySQL at ${MYSQL_HOST}:${MYSQL_PORT}..."
retries=0
until mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" --skip-ssl -e "SELECT 1" > /dev/null 2>&1; do
    retries=$((retries + 1))
    if [ $retries -ge $MAX_RETRIES ]; then
        echo "  ERROR: MySQL not ready after ${MAX_RETRIES} attempts"
        exit 1
    fi
    sleep 2
done
echo "  MySQL is ready"

# ── Check if data already seeded ─────────────────────────────────────────────
EXISTING=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" --skip-ssl "$MYSQL_DB" \
    -sN -e "SELECT COUNT(*) FROM interaction WHERE tenant_id='default' AND created_by='seed-script'" 2>/dev/null || echo "0")

if [ "$EXISTING" -ge 10 ] && [ "${FORCE_RESEED:-false}" != "true" ]; then
    echo ""
    echo "  Demo data already present (${EXISTING} interactions)."
    echo "  Set FORCE_RESEED=true to re-seed."
    echo ""
    exit 0
fi

# ── Run the seed script ─────────────────────────────────────────────────────
echo ""
echo "Seeding demo data..."
export MYSQL_MODE=direct
python3 /scripts/seed-ecommerce-data.py --clear

echo ""
echo "========================================="
echo "  Demo data seeded successfully!"
echo "========================================="
