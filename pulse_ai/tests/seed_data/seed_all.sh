#!/usr/bin/env bash
# ============================================================================
# Pulse AI Agent — Seed All Databases
# ============================================================================
# Seeds both MySQL and ClickHouse with test data for the AI agent.
#
# Prerequisites:
#   - Docker containers running (cd deploy && ./scripts/start.sh)
#   - MySQL on port 3307, ClickHouse on port 8123/9000
#
# Usage:
#   cd pulse_ai/tests/seed_data
#   ./seed_all.sh              # Seed both databases
#   ./seed_all.sh --mysql      # Seed MySQL only
#   ./seed_all.sh --clickhouse # Seed ClickHouse only
#   ./seed_all.sh --generate   # Regenerate ClickHouse SQL only (no insert)
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GENERATED_CH_FILE="${SCRIPT_DIR}/clickhouse_seed.sql"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---------------------------------------------------------------------------
# Configuration — match deploy/docker-compose.yml ports
# ---------------------------------------------------------------------------
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_DB="${MYSQL_DB:-pulse_db}"

CH_HOST="${CH_HOST:-127.0.0.1}"
CH_HTTP_PORT="${CH_HTTP_PORT:-8123}"

# ---------------------------------------------------------------------------
# Functions
# ---------------------------------------------------------------------------

generate_clickhouse_sql() {
    log_info "Generating ClickHouse seed SQL..."
    python3 "${SCRIPT_DIR}/generate_clickhouse_data.py" > "${GENERATED_CH_FILE}"
    local lines
    lines=$(wc -l < "${GENERATED_CH_FILE}" | tr -d ' ')
    log_ok "Generated ${GENERATED_CH_FILE} (${lines} lines)"
}

seed_mysql() {
    log_info "Seeding MySQL (${MYSQL_HOST}:${MYSQL_PORT})..."

    if ! command -v mysql &> /dev/null; then
        # Try Docker exec as fallback
        log_warn "mysql CLI not found, trying docker exec..."
        docker exec -i pulse-mysql mysql -u "${MYSQL_USER}" "${MYSQL_DB}" < "${SCRIPT_DIR}/mysql_seed.sql"
    else
        mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" "${MYSQL_DB}" < "${SCRIPT_DIR}/mysql_seed.sql"
    fi

    log_ok "MySQL seeded successfully"
}

seed_clickhouse() {
    # Generate if not exists
    if [ ! -f "${GENERATED_CH_FILE}" ]; then
        generate_clickhouse_sql
    fi

    log_info "Seeding ClickHouse (${CH_HOST}:${CH_HTTP_PORT})..."

    # Prefer clickhouse-client (docker exec) — it handles multi-statement natively
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'clickhouse'; then
        local ch_container
        ch_container=$(docker ps --format '{{.Names}}' | grep 'clickhouse' | head -1)
        log_info "Using docker exec → ${ch_container}"
        docker exec -i "${ch_container}" clickhouse-client --multiquery < "${GENERATED_CH_FILE}"
        log_ok "ClickHouse seeded successfully (via clickhouse-client)"
        return
    fi

    # Fallback: split into individual INSERT statements and send each via curl
    log_warn "ClickHouse container not found, falling back to HTTP API (one INSERT per request)..."

    # Create temp dir for split files
    local tmpdir
    tmpdir=$(mktemp -d)
    trap "rm -rf ${tmpdir}" RETURN

    # Strip comment/blank lines, split on INSERT INTO boundaries
    grep -v '^\s*--' "${GENERATED_CH_FILE}" | grep -v '^\s*$' | \
        csplit -z -f "${tmpdir}/part_" -b '%03d.sql' - '/^INSERT INTO/' '{*}' > /dev/null 2>&1

    local insert_count=0
    local failed=0
    for part_file in "${tmpdir}"/part_*.sql; do
        # Skip empty fragments
        [ -s "${part_file}" ] || continue
        # Only process files that contain an INSERT
        grep -q '^INSERT INTO' "${part_file}" || continue

        insert_count=$((insert_count + 1))
        local response
        response=$(curl -s -w "\n%{http_code}" \
            "http://${CH_HOST}:${CH_HTTP_PORT}/" \
            --data-binary @"${part_file}" 2>&1)

        local http_code
        http_code=$(echo "${response}" | tail -1)
        if [ "${http_code}" != "200" ]; then
            log_error "INSERT #${insert_count} failed (HTTP ${http_code})"
            echo "${response}" | head -n -1
            failed=$((failed + 1))
        fi
    done

    if [ "${failed}" -gt 0 ]; then
        log_error "${failed} of ${insert_count} INSERTs failed"
        exit 1
    fi
    log_ok "ClickHouse seeded successfully (${insert_count} INSERTs via HTTP)"
}

verify_data() {
    log_info "Verifying seeded data..."

    echo ""
    log_info "MySQL — Interactions:"
    if command -v mysql &> /dev/null; then
        mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" "${MYSQL_DB}" \
            -e "SELECT name, status, created_by FROM interaction WHERE is_archived = 0;" 2>/dev/null || true
    else
        docker exec -i pulse-mysql mysql -u "${MYSQL_USER}" "${MYSQL_DB}" \
            -e "SELECT name, status, created_by FROM interaction WHERE is_archived = 0;" 2>/dev/null || true
    fi

    echo ""
    log_info "MySQL — Alerts:"
    if command -v mysql &> /dev/null; then
        mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" "${MYSQL_DB}" \
            -e "SELECT a.name, a.scope, s.state FROM alerts a JOIN alert_scope s ON a.id = s.alert_id WHERE a.is_active = TRUE AND s.is_active = TRUE;" 2>/dev/null || true
    else
        docker exec -i pulse-mysql mysql -u "${MYSQL_USER}" "${MYSQL_DB}" \
            -e "SELECT a.name, a.scope, s.state FROM alerts a JOIN alert_scope s ON a.id = s.alert_id WHERE a.is_active = TRUE AND s.is_active = TRUE;" 2>/dev/null || true
    fi

    echo ""
    log_info "ClickHouse — Trace counts by interaction:"
    curl -s "http://${CH_HOST}:${CH_HTTP_PORT}/" \
        --data "SELECT SpanName, COUNT() as cnt, countIf(StatusCode = 'Error') as errors, countIf(has(Events.Name, 'device.crash')) as crashes FROM otel.otel_traces WHERE PulseType = 'interaction' GROUP BY SpanName ORDER BY cnt DESC FORMAT PrettyCompact" 2>/dev/null || true

    echo ""
    log_info "ClickHouse — Platform breakdown:"
    curl -s "http://${CH_HOST}:${CH_HTTP_PORT}/" \
        --data "SELECT Platform, COUNT() as cnt FROM otel.otel_traces WHERE PulseType = 'interaction' GROUP BY Platform FORMAT PrettyCompact" 2>/dev/null || true

    echo ""
    log_ok "Verification complete"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

case "${1:-all}" in
    --mysql)
        seed_mysql
        ;;
    --clickhouse)
        seed_clickhouse
        ;;
    --generate)
        generate_clickhouse_sql
        ;;
    --verify)
        verify_data
        ;;
    all|"")
        generate_clickhouse_sql
        echo ""
        seed_mysql
        echo ""
        seed_clickhouse
        echo ""
        verify_data
        ;;
    *)
        echo "Usage: $0 [--mysql | --clickhouse | --generate | --verify | all]"
        exit 1
        ;;
esac

echo ""
log_ok "Done!"
