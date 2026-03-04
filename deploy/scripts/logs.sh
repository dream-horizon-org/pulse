#!/bin/bash

# ============================================================================
# Pulse Observability - Logs Script
# Shows logs from Pulse containers.
# Uses Docker Compose if available, otherwise falls back to Docker CLI.
#
# Usage:
#   ./logs.sh [--no-follow] [--tail N] [ui|server|cron|mysql|clickhouse|otel]
# ============================================================================

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

# ── Parse arguments ────────────────────────────────────────────────────────
FOLLOW="-f"
TAIL=""
SERVICE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-follow)
            FOLLOW=""
            shift
            ;;
        --tail)
            if [ -z "${2:-}" ]; then
                print_error "--tail requires a numeric argument (e.g. --tail 100)"
                exit 1
            fi
            TAIL="--tail=$2"
            shift 2
            ;;
        ui|pulse-ui)
            SERVICE="$CONTAINER_UI"
            shift
            ;;
        server|pulse-server)
            SERVICE="$CONTAINER_SERVER"
            shift
            ;;
        cron|alerts-cron|pulse-alerts-cron)
            SERVICE="$CONTAINER_ALERTS_CRON"
            shift
            ;;
        mysql)
            SERVICE="$CONTAINER_MYSQL"
            shift
            ;;
        clickhouse)
            SERVICE="$CONTAINER_CLICKHOUSE"
            shift
            ;;
        otel|otel-collector)
            SERVICE="$CONTAINER_OTEL_COLLECTOR"
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--no-follow] [--tail N] [ui|server|cron|mysql|clickhouse|otel]"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: $0 [--no-follow] [--tail N] [ui|server|cron|mysql|clickhouse|otel]"
            exit 1
            ;;
    esac
done

# ── Compose path (when showing all or a single compose service) ──────────
if has_compose && [ -z "$SERVICE" ]; then
    cd "$DEPLOY_DIR"
    echo -e "${BLUE}Showing logs for all services...${NC}"
    # shellcheck disable=SC2086
    run_compose logs $FOLLOW $TAIL
    exit 0
fi

# ── Docker CLI path ──────────────────────────────────────────────────────
if [ -n "$SERVICE" ]; then
    echo -e "${BLUE}Showing logs for $SERVICE ...${NC}"
    # shellcheck disable=SC2086
    docker logs $FOLLOW $TAIL "$SERVICE"
else
    echo -e "${BLUE}Showing logs for all Pulse containers ...${NC}"
    echo -e "${YELLOW}(Ctrl+C to stop)${NC}"
    echo ""

    PIDS=()
    for container in "${ALL_CONTAINERS[@]}"; do
        if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
            # shellcheck disable=SC2086
            docker logs $FOLLOW $TAIL "$container" 2>&1 | sed "s/^/[$container] /" &
            PIDS+=($!)
        fi
    done

    if [ ${#PIDS[@]} -eq 0 ]; then
        print_warning "No running Pulse containers found"
        exit 0
    fi

    trap 'kill "${PIDS[@]}" 2>/dev/null; exit 0' INT TERM
    wait "${PIDS[@]}" 2>/dev/null
fi
