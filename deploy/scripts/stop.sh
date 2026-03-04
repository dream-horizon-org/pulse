#!/bin/bash

# ============================================================================
# Pulse Observability - Stop Script
# Stops and removes Pulse containers.
# Uses Docker Compose if available, otherwise falls back to Docker CLI.
#
# Usage:
#   ./stop.sh [-v|--volumes] [--all] [ui|server|cron|mysql|clickhouse|otel]
# ============================================================================

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Pulse Observability - Stop Script        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# ── Parse arguments ────────────────────────────────────────────────────────
REMOVE_VOLUMES=""
REMOVE_NETWORK=""
SERVICES=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--volumes)
            REMOVE_VOLUMES="true"
            shift
            ;;
        --all)
            REMOVE_VOLUMES="true"
            REMOVE_NETWORK="true"
            shift
            ;;
        ui|pulse-ui)
            SERVICES+=("$CONTAINER_UI")
            shift
            ;;
        server|pulse-server)
            SERVICES+=("$CONTAINER_SERVER")
            shift
            ;;
        cron|alerts-cron|pulse-alerts-cron)
            SERVICES+=("$CONTAINER_ALERTS_CRON")
            shift
            ;;
        mysql)
            SERVICES+=("$CONTAINER_MYSQL")
            shift
            ;;
        clickhouse)
            SERVICES+=("$CONTAINER_CLICKHOUSE")
            shift
            ;;
        otel|otel-collector)
            SERVICES+=("$CONTAINER_OTEL_COLLECTOR")
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [-v|--volumes] [--all] [ui|server|cron|mysql|clickhouse|otel]"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: $0 [-v|--volumes] [--all] [ui|server|cron|mysql|clickhouse|otel]"
            exit 1
            ;;
    esac
done

# ── Compose path (when no specific services selected) ────────────────────
if has_compose && [ ${#SERVICES[@]} -eq 0 ]; then
    cd "$DEPLOY_DIR"
    COMPOSE_ARGS=""
    [ "$REMOVE_VOLUMES" = "true" ] && COMPOSE_ARGS="-v"

    print_info "Stopping services via Docker Compose..."
    # shellcheck disable=SC2086
    run_compose down $COMPOSE_ARGS

    if [ "$REMOVE_NETWORK" = "true" ]; then
        if docker network inspect "$NETWORK_NAME" > /dev/null 2>&1; then
            docker network rm "$NETWORK_NAME" > /dev/null
            print_success "Removed network $NETWORK_NAME"
        fi
    fi

    echo ""
    print_success "All done"
    exit 0
fi

# ── Docker CLI path ──────────────────────────────────────────────────────
# Default: stop all containers in reverse dependency order
if [ ${#SERVICES[@]} -eq 0 ]; then
    SERVICES=(
        "$CONTAINER_ALERTS_CRON"
        "$CONTAINER_UI"
        "$CONTAINER_SERVER"
        "$CONTAINER_OTEL_COLLECTOR"
        "$CONTAINER_CLICKHOUSE_INIT"
        "$CONTAINER_CLICKHOUSE"
        "$CONTAINER_MYSQL"
    )
fi

print_info "Stopping containers..."
echo ""

for container in "${SERVICES[@]}"; do
    if docker ps -a --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -n "  Stopping $container ... "
        docker stop "$container" > /dev/null 2>&1 || true
        docker rm "$container" > /dev/null 2>&1 || true
        echo -e "${GREEN}done${NC}"
    else
        echo -e "  $container ${YELLOW}not found (skipped)${NC}"
    fi
done

echo ""
print_success "Containers stopped"

if [ "$REMOVE_VOLUMES" = "true" ]; then
    echo ""
    print_info "Removing data volumes..."
    for vol in "$VOLUME_MYSQL" "$VOLUME_CLICKHOUSE"; do
        if docker volume inspect "$vol" > /dev/null 2>&1; then
            docker volume rm "$vol" > /dev/null
            print_success "Removed volume $vol"
        else
            echo -e "  Volume $vol ${YELLOW}not found (skipped)${NC}"
        fi
    done
fi

if [ "$REMOVE_NETWORK" = "true" ]; then
    echo ""
    print_info "Removing network..."
    if docker network inspect "$NETWORK_NAME" > /dev/null 2>&1; then
        docker network rm "$NETWORK_NAME" > /dev/null
        print_success "Removed network $NETWORK_NAME"
    else
        echo -e "  Network $NETWORK_NAME ${YELLOW}not found (skipped)${NC}"
    fi
fi

echo ""
print_success "All done"
