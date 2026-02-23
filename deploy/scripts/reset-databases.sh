#!/bin/bash

# ============================================================================
# Pulse Observability - Reset Databases Script
# Removes all database volumes and restarts services from scratch.
#
# Usage:
#   ./reset-databases.sh
# ============================================================================

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

echo -e "${YELLOW}╔════════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║   Pulse - Reset Databases                  ║${NC}"
echo -e "${YELLOW}╚════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${RED}WARNING: This will delete ALL database data!${NC}"
echo "This includes:"
echo "  - MySQL data (pulse_db)"
echo "  - ClickHouse data (otel database)"
echo ""
read -p "Are you sure you want to continue? (yes/no): " -r
echo

if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    print_error "Aborted"
    exit 1
fi

# ── Step 1: Stop everything ───────────────────────────────────────────────
print_section "Stopping all services"
"$SCRIPT_DIR/stop.sh"

# ── Step 2: Remove database volumes ──────────────────────────────────────
print_section "Removing database volumes"

for vol in "$VOLUME_MYSQL" "$VOLUME_CLICKHOUSE"; do
    if docker volume inspect "$vol" > /dev/null 2>&1; then
        docker volume rm "$vol" > /dev/null
        print_success "Removed volume $vol"
    else
        echo -e "  Volume $vol ${YELLOW}not found (skipped)${NC}"
    fi
done

# ── Step 3: Restart services ─────────────────────────────────────────────
print_section "Starting services (init scripts will run automatically)"
"$SCRIPT_DIR/start.sh" -d

# ── Step 4: Verify ───────────────────────────────────────────────────────
print_section "Verifying databases"

print_info "Waiting for databases to settle..."
sleep 10

load_env

echo ""
echo "MySQL tables:"
docker exec "$CONTAINER_MYSQL" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" -e "SHOW TABLES;" 2>/dev/null || echo "  (MySQL still initializing...)"

echo ""
echo "ClickHouse tables:"
docker exec "$CONTAINER_CLICKHOUSE" clickhouse-client --query "SHOW TABLES FROM ${OTEL_CLICKHOUSE_DATABASE}" 2>/dev/null || echo "  (ClickHouse still initializing...)"

echo ""
print_success "Databases reset complete!"
echo ""
echo "If tables are not shown above, wait a minute and run:"
echo "  docker ps --filter network=$NETWORK_NAME"
echo "  docker logs $CONTAINER_MYSQL"
echo "  docker logs $CONTAINER_CLICKHOUSE"
