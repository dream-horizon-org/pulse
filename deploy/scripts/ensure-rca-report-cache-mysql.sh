#!/usr/bin/env bash
# Apply rca_report_cache table if missing (MySQL was created before this DDL).
# Usage: ./deploy/scripts/ensure-rca-report-cache-mysql.sh

set -e
SEED_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SEED_SCRIPT_DIR/.." && pwd)"
SQL_FILE="$DEPLOY_DIR/db/ensure-rca-report-cache.sql"

if [ -f "$SEED_SCRIPT_DIR/common.sh" ]; then
  # shellcheck source=common.sh
  source "$SEED_SCRIPT_DIR/common.sh"
  load_env
else
  if [ -f "$DEPLOY_DIR/.env" ]; then
    set -a
    # shellcheck source=../.env
    . "$DEPLOY_DIR/.env"
    set +a
  fi
fi

CONTAINER_MYSQL="${CONTAINER_MYSQL:-pulse-mysql}"
MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
MYSQL_DB="${MYSQL_DATABASE:-pulse_db}"

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_MYSQL}$"; then
  echo "Error: container '$CONTAINER_MYSQL' is not running."
  exit 1
fi

echo "Applying $SQL_FILE to $MYSQL_DB on $CONTAINER_MYSQL ..."
docker exec -i "$CONTAINER_MYSQL" mysql -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$SQL_FILE"
echo "Done. Table rca_report_cache should exist."
