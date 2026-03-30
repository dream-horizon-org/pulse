#!/usr/bin/env bash
# =============================================================================
# Import a mysqldump of PROD (pulse_db), apply prod→local migration, then emit
# a new schema-only .sql file suitable for diffing against deploy/local_schema.sql.
#
# IMPORTANT
# - deploy/local_schema.sql is a REFERENCE (mysqldump output). Do not "run" it
#   as a migration — use deploy/migrations/20260326_prod_align_local_schema.sql.
# - The migration assumes prod-shaped FK/index names (see migration comments).
# - If prod data includes symbol_files rows, athena_job NULL project_id, or
#   tenants NULL tenant_id, edit the migration DATA REQUIRED sections first or
#   use a schema-only prod dump (mysqldump --no-data) for a clean regeneration.
#
# Usage:
#   cd deploy && ./scripts/mysql-generate-aligned-schema-from-prod-dump.sh \
#     /path/to/prod_schema.sql [output.sql]
#
# Environment (defaults suit local Docker MySQL from deploy .env):
#   MYSQL_HOST      default 127.0.0.1
#   MYSQL_PORT      default 3307
#   MYSQL_USER      default pulse_user
#   MYSQL_PASSWORD  default pulse_password
#   MYSQL_DATABASE  default pulse_db
#   RECREATE_DB     default 0 — set to 1 to DROP DATABASE + CREATE before import
#                   (requires MySQL user with DROP/CREATE privilege, often root)
#
# GTID lines in prod dumps are stripped so import works on non-replica MySQL.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
MIGRATION_SQL="${DEPLOY_DIR}/migrations/20260326_prod_align_local_schema.sql"

PROD_DUMP="${1:-}"
OUTPUT_SQL="${2:-${DEPLOY_DIR}/local_schema.generated.sql}"

if [[ -z "${PROD_DUMP}" || ! -f "${PROD_DUMP}" ]]; then
  echo "Usage: $0 /path/to/prod_schema.sql [output.sql]" >&2
  exit 1
fi

if [[ ! -f "${MIGRATION_SQL}" ]]; then
  echo "Missing migration: ${MIGRATION_SQL}" >&2
  exit 1
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
MYSQL_DATABASE="${MYSQL_DATABASE:-pulse_db}"
RECREATE_DB="${RECREATE_DB:-0}"

mysql_exec() {
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" \
    --protocol=TCP -u"${MYSQL_USER}" -N -e "$1"
}

mysql_import() {
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" \
    --protocol=TCP -u"${MYSQL_USER}" "${MYSQL_DATABASE}" "$@"
}

mysqldump_schema() {
  # Table-only dump (no CREATE DATABASE) to resemble deploy/local_schema.sql style.
  MYSQL_PWD="${MYSQL_PASSWORD}" mysqldump -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" \
    --protocol=TCP -u"${MYSQL_USER}" \
    --no-data \
    --set-gtid-purged=OFF \
    --single-transaction \
    "${MYSQL_DATABASE}"
}

echo "==> Target: ${MYSQL_USER}@${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
echo "==> Prod dump: ${PROD_DUMP}"
echo "==> Output schema: ${OUTPUT_SQL}"

TMP_FILTERED="$(mktemp -t prod_schema_filtered.XXXXXX.sql)"
cleanup() { rm -f "${TMP_FILTERED}"; }
trap cleanup EXIT

# Strip GTID purge (breaks many standalone MySQL imports).
grep -v 'GTID_PURGED' "${PROD_DUMP}" > "${TMP_FILTERED}"

if [[ "${RECREATE_DB}" == "1" ]]; then
  echo "==> RECREATE_DB=1: dropping and creating ${MYSQL_DATABASE}"
  mysql_exec "DROP DATABASE IF EXISTS \`${MYSQL_DATABASE}\`;"
  mysql_exec "CREATE DATABASE \`${MYSQL_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
fi

echo "==> Importing prod dump (filtered)..."
mysql_import < "${TMP_FILTERED}"

echo "==> Applying migration ${MIGRATION_SQL}..."
mysql_import < "${MIGRATION_SQL}"

echo "==> Writing schema-only dump to ${OUTPUT_SQL}..."
mysqldump_schema > "${OUTPUT_SQL}"

echo "Done. Compare with:"
echo "  diff -u ${DEPLOY_DIR}/local_schema.sql ${OUTPUT_SQL} | head -200"
