#!/bin/bash

# ============================================================================
# Pulse Observability - Start Script
# Starts all containers. Uses Docker Compose if available, otherwise falls
# back to Docker CLI with dependency-ordered health-check gating.
#
# Usage:
#   ./start.sh [-d|--detach] [--build]
# ============================================================================

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Pulse Observability - Start Script       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# ── Pre-flight ─────────────────────────────────────────────────────────────
check_docker
load_env

# ── Parse arguments ────────────────────────────────────────────────────────
DETACHED=""
BUILD_FIRST=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--detach)  DETACHED="true"; shift ;;
        --build)      BUILD_FIRST="true"; shift ;;
        -h|--help)
            echo "Usage: $0 [-d|--detach] [--build]"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: $0 [-d|--detach] [--build]"
            exit 1
            ;;
    esac
done

# ── Compose path ──────────────────────────────────────────────────────────
if has_compose; then
    cd "$DEPLOY_DIR"
    COMPOSE_ARGS=""
    # Let Compose handle --build natively (avoids building twice)
    [ "$BUILD_FIRST" = "true" ] && COMPOSE_ARGS="$COMPOSE_ARGS --build"
    [ "$DETACHED" = "true" ] && COMPOSE_ARGS="$COMPOSE_ARGS -d"

    print_info "Starting services via Docker Compose..."
    # shellcheck disable=SC2086
    run_compose up $COMPOSE_ARGS

    if [ "$DETACHED" = "true" ]; then
        echo ""
        print_info "Waiting for databases to initialize..."
        wait_for_healthy "$CONTAINER_MYSQL" 120
        wait_for_healthy "$CONTAINER_CLICKHOUSE" 120

        if ! verify_mysql_init; then
            print_info "Fix the init script and re-run: ./scripts/reset-databases.sh"
        fi
        if ! verify_clickhouse_init; then
            print_info "Check logs: docker logs $CONTAINER_CLICKHOUSE"
        fi

        echo ""
        print_success "Services started in detached mode"
        echo ""
        print_info "Container status:"
        run_compose ps
        echo ""
        echo -e "${CYAN}Access points:${NC}"
        echo -e "  UI:  ${GREEN}http://localhost:3000${NC}"
        echo -e "  API: ${GREEN}http://localhost:8080${NC}"
        echo ""
        echo -e "${CYAN}View logs:${NC}  ./logs.sh"
    fi
    exit 0
fi

# ── Optional build step (CLI mode only -- Compose handles --build natively)
if [ "$BUILD_FIRST" = "true" ]; then
    print_section "Building Docker images"
    "$SCRIPT_DIR/build.sh"
fi

# ── Docker CLI path ──────────────────────────────────────────────────────
print_section "Preparing infrastructure"
ensure_network
ensure_volumes

# ── Phase 1: Databases ───────────────────────────────────────────────────
print_section "Phase 1: Starting databases"

remove_container "$CONTAINER_MYSQL"
print_info "Starting $CONTAINER_MYSQL ..."

docker run -d \
    --name "$CONTAINER_MYSQL" \
    --network "$NETWORK_NAME" \
    --network-alias mysql \
    --restart unless-stopped \
    -p 3307:3306 \
    -e "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}" \
    -e "MYSQL_DATABASE=${MYSQL_DATABASE}" \
    -e "MYSQL_USER=${MYSQL_USER}" \
    -e "MYSQL_PASSWORD=${MYSQL_PASSWORD}" \
    -v "${VOLUME_MYSQL}:/var/lib/mysql" \
    -v "${DEPLOY_DIR}/db/mysql-init.sql:/docker-entrypoint-initdb.d/init.sql:ro" \
    --health-cmd "mysqladmin ping -h localhost -u${MYSQL_USER} -p${MYSQL_PASSWORD}" \
    --health-interval 10s \
    --health-timeout 5s \
    --health-retries 5 \
    --health-start-period 30s \
    "$IMAGE_MYSQL" > /dev/null

print_success "$CONTAINER_MYSQL container started"

remove_container "$CONTAINER_CLICKHOUSE"
print_info "Starting $CONTAINER_CLICKHOUSE ..."

docker run -d \
    --name "$CONTAINER_CLICKHOUSE" \
    --network "$NETWORK_NAME" \
    --network-alias clickhouse \
    --restart unless-stopped \
    -p 8123:8123 \
    -p 9000:9000 \
    -e "CLICKHOUSE_DB=${OTEL_CLICKHOUSE_DATABASE}" \
    -e "CLICKHOUSE_USER=${OTEL_CLICKHOUSE_USER}" \
    -e "CLICKHOUSE_PASSWORD=${OTEL_CLICKHOUSE_PASSWORD}" \
    -e CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT=1 \
    -v "${VOLUME_CLICKHOUSE}:/var/lib/clickhouse" \
    -v "${ROOT_DIR}/backend/ingestion/clickhouse-otel-schema.sql:/docker-entrypoint-initdb.d/init.sql:ro" \
    --health-cmd 'clickhouse-client --query "SELECT 1"' \
    --health-interval 10s \
    --health-timeout 5s \
    --health-retries 5 \
    --health-start-period 30s \
    "$IMAGE_CLICKHOUSE" > /dev/null

print_success "$CONTAINER_CLICKHOUSE container started"

print_info "Waiting for databases to become healthy..."
wait_for_healthy "$CONTAINER_MYSQL" 120
wait_for_healthy "$CONTAINER_CLICKHOUSE" 120

if ! verify_mysql_init; then
    print_error "MySQL initialization failed. Fix the init script and run: ./reset-databases.sh"
    exit 1
fi

# ── Phase 2: ClickHouse init + OTEL Collector ────────────────────────────
print_section "Phase 2: Initialising ClickHouse tables & OTEL Collector"

remove_container "$CONTAINER_CLICKHOUSE_INIT"
print_info "Running $CONTAINER_CLICKHOUSE_INIT (one-shot) ..."

docker run --rm \
    --name "$CONTAINER_CLICKHOUSE_INIT" \
    --network "$NETWORK_NAME" \
    -e "CLICKHOUSE_HOST=clickhouse" \
    -e "CLICKHOUSE_USER=${OTEL_CLICKHOUSE_USER}" \
    -e "CLICKHOUSE_PASSWORD=${OTEL_CLICKHOUSE_PASSWORD}" \
    -e "CLICKHOUSE_DB=${OTEL_CLICKHOUSE_DATABASE}" \
    -v "${SCRIPT_DIR}/init-clickhouse.sh:/scripts/init-clickhouse.sh:ro" \
    -v "${ROOT_DIR}/backend/ingestion/clickhouse-otel-schema.sql:/init/clickhouse-otel-schema.sql:ro" \
    "$IMAGE_CLICKHOUSE" \
    /bin/bash /scripts/init-clickhouse.sh

print_success "ClickHouse tables initialised"

if ! verify_clickhouse_init; then
    print_error "ClickHouse table initialization failed. Check the schema file."
    exit 1
fi

remove_container "$CONTAINER_OTEL_COLLECTOR"
print_info "Starting $CONTAINER_OTEL_COLLECTOR ..."

docker run -d \
    --name "$CONTAINER_OTEL_COLLECTOR" \
    --network "$NETWORK_NAME" \
    --restart unless-stopped \
    -p 4317:4317 \
    -p 4318:4318 \
    -p 8888:8888 \
    -p 13133:13133 \
    -e "CLICKHOUSE_ENDPOINT=tcp://clickhouse:9000" \
    -e "CLICKHOUSE_DATABASE=${OTEL_CLICKHOUSE_DATABASE}" \
    -e "CLICKHOUSE_USER=${OTEL_CLICKHOUSE_USER}" \
    -e "CLICKHOUSE_PASSWORD=${OTEL_CLICKHOUSE_PASSWORD}" \
    -v "${ROOT_DIR}/backend/ingestion/otel-collector.yaml:/etc/otel-collector.yaml:ro" \
    "$IMAGE_OTEL_COLLECTOR" \
    --config=/etc/otel-collector.yaml > /dev/null

print_success "$CONTAINER_OTEL_COLLECTOR container started"

# ── Phase 3: Pulse Server ────────────────────────────────────────────────
print_section "Phase 3: Starting Pulse Server"

remove_container "$CONTAINER_SERVER"
print_info "Starting $CONTAINER_SERVER ..."

docker run -d \
    --name "$CONTAINER_SERVER" \
    --network "$NETWORK_NAME" \
    --restart unless-stopped \
    -p 8080:8080 \
    \
    -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" \
    -e "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" \
    -e "AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN}" \
    \
    -e MYSQL_WRITER_HOST=mysql \
    -e MYSQL_READER_HOST=mysql \
    -e "MYSQL_DATABASE=${MYSQL_DATABASE}" \
    -e "MYSQL_USER=${MYSQL_USER}" \
    -e "MYSQL_PASSWORD=${MYSQL_PASSWORD}" \
    -e "MYSQL_WRITER_MAX_POOL_SIZE=${MYSQL_WRITER_MAX_POOL_SIZE}" \
    -e "MYSQL_READER_MAX_POOL_SIZE=${MYSQL_READER_MAX_POOL_SIZE}" \
    \
    -e "CONFIG_SERVICE_APPLICATION_CRONMANAGERBASEURL=${CONFIG_SERVICE_APPLICATION_CRONMANAGERBASEURL}" \
    -e "CONFIG_SERVICE_APPLICATION_SERVICEURL=${CONFIG_SERVICE_APPLICATION_SERVICEURL}" \
    -e "CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHCLIENTID=${CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHCLIENTID}" \
    -e "CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHENABLED=${CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHENABLED}" \
    -e "CONFIG_SERVICE_APPLICATION_JWTSECRET=${CONFIG_SERVICE_APPLICATION_JWTSECRET}" \
    -e "CONFIG_SERVICE_APPLICATION_WEBHOOKURL=${CONFIG_SERVICE_APPLICATION_WEBHOOKURL}" \
    \
    -e "CLICKHOUSE_R2DBC_URL=r2dbc:clickhouse:http://clickhouse:8123/${OTEL_CLICKHOUSE_DATABASE}" \
    -e "CLICKHOUSE_USERNAME=${OTEL_CLICKHOUSE_USER}" \
    -e "CLICKHOUSE_PASSWORD=${OTEL_CLICKHOUSE_PASSWORD}" \
    -e CLICKHOUSE_HOST=clickhouse \
    -e CLICKHOUSE_PORT=8123 \
    -e "ENCRYPTION_MASTER_KEY=${VAULT_ENCRYPTION_MASTER_KEY}" \
    \
    -e "S3_BUCKET_NAME=${CONFIG_S3_BUCKET_NAME}" \
    -e "CONFIG_DETAILS_S3_FILE_PATH=${CONFIG_DETAILS_S3_FILE_PATH}" \
    -e "INTERACTION_DETAILS_S3_FILE_PATH=${INTERACTION_DETAILS_S3_FILE_PATH}" \
    \
    -e "CLOUDFRONT_DISTRIBUTION_ID=${CONFIG_CLOUDFRONT_DISTRIBUTION_ID}" \
    -e "CONFIG_CLOUDFRONT_ASSET_PATH=${CONFIG_CLOUDFRONT_ASSET_PATH}" \
    -e "INTERACTION_CLOUDFRONT_ASSET_PATH=${INTERACTION_CLOUDFRONT_ASSET_PATH}" \
    \
    -e "INTERACTION_CONFIG_URL=${INTERACTION_CONFIG_URL}" \
    -e "LOGS_COLLECTOR_URL=${LOGS_COLLECTOR_URL}" \
    -e "METRIC_COLLECTOR_URL=${METRIC_COLLECTOR_URL}" \
    -e "SPAN_COLLECTOR_URL=${SPAN_COLLECTOR_URL}" \
    -e "CUSTOM_EVENT_COLLECTOR_URL=${CUSTOM_EVENT_COLLECTOR_URL}" \
    \
    -e "CONFIG_SERVICE_APPLICATION_QUERY_ENGINE=${CONFIG_SERVICE_APPLICATION_QUERY_ENGINE}" \
    -e "CONFIG_SERVICE_APPLICATION_ATHENA_REGION=${CONFIG_SERVICE_APPLICATION_ATHENA_REGION}" \
    -e "CONFIG_SERVICE_APPLICATION_ATHENA_DATABASE=${CONFIG_SERVICE_APPLICATION_ATHENA_DATABASE}" \
    -e "CONFIG_SERVICE_APPLICATION_ATHENA_OUTPUT_LOCATION=${CONFIG_SERVICE_APPLICATION_ATHENA_OUTPUT_LOCATION}" \
    -e "CONFIG_SERVICE_APPLICATION_GCP_PROJECT_ID=${CONFIG_SERVICE_APPLICATION_GCP_PROJECT_ID}" \
    \
    -v "${ROOT_DIR}/backend/server/src/main/resources/config:/app/config:ro" \
    \
    --health-cmd 'curl -f http://localhost:8080/healthcheck' \
    --health-interval 30s \
    --health-timeout 10s \
    --health-retries 3 \
    --health-start-period 60s \
    "$IMAGE_SERVER" > /dev/null

print_success "$CONTAINER_SERVER container started"

print_info "Waiting for $CONTAINER_SERVER to become healthy..."
wait_for_healthy "$CONTAINER_SERVER" 180

# ── Phase 4: Pulse UI & Alerts Cron ──────────────────────────────────────
print_section "Phase 4: Starting Pulse UI & Alerts Cron"

remove_container "$CONTAINER_UI"
print_info "Starting $CONTAINER_UI ..."

docker run -d \
    --name "$CONTAINER_UI" \
    --network "$NETWORK_NAME" \
    --restart unless-stopped \
    -p 3000:8080 \
    -e NODE_ENV=production \
    --health-cmd 'wget --spider -q http://127.0.0.1:8080/healthcheck.txt' \
    --health-interval 30s \
    --health-timeout 10s \
    --health-retries 3 \
    --health-start-period 10s \
    "$IMAGE_UI" > /dev/null

print_success "$CONTAINER_UI container started"

remove_container "$CONTAINER_ALERTS_CRON"
print_info "Starting $CONTAINER_ALERTS_CRON ..."

docker run -d \
    --name "$CONTAINER_ALERTS_CRON" \
    --network "$NETWORK_NAME" \
    --restart unless-stopped \
    -p 4000:4000 \
    \
    -e MYSQL_WRITER_HOST=mysql \
    -e MYSQL_READER_HOST=mysql \
    -e "MYSQL_DATABASE=${MYSQL_DATABASE}" \
    -e "MYSQL_USER=${MYSQL_USER}" \
    -e "MYSQL_PASSWORD=${MYSQL_PASSWORD}" \
    \
    -e "CONFIG_SERVICE_APPLICATION_PULSESERVERURL=${CONFIG_SERVICE_APPLICATION_PULSESERVERURL}" \
    -e "CONFIG_SERVICE_APPLICATION_WEBHOOKURL=${CONFIG_SERVICE_APPLICATION_WEBHOOKURL}" \
    \
    -v "${ROOT_DIR}/backend/pulse-alerts-cron/src/main/resources/config:/app/config:ro" \
    \
    --health-cmd 'curl -f http://localhost:4000/healthcheck' \
    --health-interval 30s \
    --health-timeout 10s \
    --health-retries 3 \
    --health-start-period 60s \
    "$IMAGE_ALERTS_CRON" > /dev/null

print_success "$CONTAINER_ALERTS_CRON container started"

# ── Summary ──────────────────────────────────────────────────────────────
print_section "All containers started"

if [ "$DETACHED" = "true" ]; then
    echo -e "${BLUE}Container status:${NC}"
    docker ps --filter "network=$NETWORK_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    echo -e "${CYAN}Access points:${NC}"
    echo -e "  Frontend (UI):      ${GREEN}http://localhost:3000${NC}"
    echo -e "  Backend API:        ${GREEN}http://localhost:8080${NC}"
    echo -e "  MySQL:              ${GREEN}localhost:3307${NC}"
    echo -e "  ClickHouse HTTP:    ${GREEN}http://localhost:8123${NC}"
    echo -e "  OTEL gRPC:          ${GREEN}localhost:4317${NC}"
    echo ""
    echo -e "${CYAN}View logs:${NC}  ./logs.sh"
else
    print_info "All containers are running. Attaching to logs (Ctrl+C to detach)..."
    echo ""
    docker logs -f "$CONTAINER_SERVER" &
    docker logs -f "$CONTAINER_UI" &
    docker logs -f "$CONTAINER_ALERTS_CRON" &
    wait
fi
