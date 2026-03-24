#!/usr/bin/env bash
# ============================================================================
# Seed e-commerce demo data (from host)
# ============================================================================
# Runs deploy/scripts/seed-ecommerce-data.py with env from deploy/.env.
# Requires: MySQL and ClickHouse running (e.g. via start.sh).
#
# Usage:
#   ./deploy/scripts/seed-ecommerce.sh         # Seed (skip if already seeded)
#   ./deploy/scripts/seed-ecommerce.sh --clear # Wipe and re-seed
# ============================================================================

set -e
SEED_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SEED_SCRIPT_DIR/.." && pwd)"
cd "$DEPLOY_DIR"

if [ -f "$SEED_SCRIPT_DIR/common.sh" ]; then
  # shellcheck source=common.sh
  source "$SEED_SCRIPT_DIR/common.sh"
  load_env
else
  if [ -f .env ]; then
    set -a
    # shellcheck source=../.env
    . ./.env
    set +a
  fi
fi

# ClickHouse: from host use localhost; from inside Docker use clickhouse
CH_HOST="${CH_HOST:-127.0.0.1}"
CH_PORT="${CH_PORT:-8123}"
export CH_HOST CH_PORT
export CH_DB="${OTEL_CLICKHOUSE_DATABASE:-otel}"
export CH_USER="${OTEL_CLICKHOUSE_USER:-pulse_user}"
export CH_PASSWORD="${OTEL_CLICKHOUSE_PASSWORD:-pulse_password}"

# MySQL: use docker exec by default so no local mysql client needed
export MYSQL_MODE="${MYSQL_MODE:-docker}"
export MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
export MYSQL_PORT="${MYSQL_PORT:-3307}"
export MYSQL_USER="${MYSQL_USER:-pulse_user}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
export MYSQL_DB="${MYSQL_DATABASE:-pulse_db}"

# Ensure MySQL container is running when using docker mode
if [ "$MYSQL_MODE" = "docker" ]; then
  CONTAINER_MYSQL="${CONTAINER_MYSQL:-pulse-mysql}"
  if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_MYSQL}$"; then
    echo "Error: MySQL container '$CONTAINER_MYSQL' is not running. Start the stack first (e.g. ./scripts/start.sh -d)."
    exit 1
  fi
fi

# Ensure ClickHouse is reachable
if ! curl -sf "http://${CH_HOST}:${CH_PORT}/ping" > /dev/null 2>&1; then
  echo "Error: ClickHouse is not reachable at ${CH_HOST}:${CH_PORT}. Start the stack first (e.g. ./scripts/start.sh -d)."
  exit 1
fi

echo "Seeding e-commerce data (CH: ${CH_HOST}:${CH_PORT}, MySQL: ${MYSQL_MODE})..."
exec python3 "$SEED_SCRIPT_DIR/seed-ecommerce-data.py" "$@"
