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

# ── Phase 2: Kafka + MinIO ────────────────────────────────────────────────
print_section "Phase 2: Starting Kafka & MinIO"

remove_container "$CONTAINER_KAFKA"
print_info "Starting $CONTAINER_KAFKA ..."

docker run -d \
    --name "$CONTAINER_KAFKA" \
    --network "$NETWORK_NAME" \
    --network-alias kafka \
    --restart unless-stopped \
    -p 9092:9092 \
    -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
    -e KAFKA_NODE_ID=1 \
    -e KAFKA_PROCESS_ROLES=broker,controller \
    -e "KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093" \
    -e "KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093" \
    -e "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092" \
    -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    -e "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT" \
    -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    -e KAFKA_LOG_DIRS=/var/lib/kafka/data \
    -e KAFKA_NUM_PARTITIONS=12 \
    -e KAFKA_LOG_RETENTION_HOURS=1 \
    -v "${VOLUME_KAFKA}:/var/lib/kafka/data" \
    --health-cmd "kafka-topics --bootstrap-server localhost:9092 --list" \
    --health-interval 5s \
    --health-timeout 5s \
    --health-retries 10 \
    --health-start-period 30s \
    "$IMAGE_KAFKA" > /dev/null

print_success "$CONTAINER_KAFKA container started"

remove_container "$CONTAINER_MINIO"
print_info "Starting $CONTAINER_MINIO ..."

docker run -d \
    --name "$CONTAINER_MINIO" \
    --network "$NETWORK_NAME" \
    --network-alias minio \
    --restart unless-stopped \
    -p 9100:9000 \
    -p 9101:9001 \
    -e "MINIO_ROOT_USER=${MINIO_ROOT_USER}" \
    -e "MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}" \
    -v "${VOLUME_MINIO}:/data" \
    --health-cmd "mc ready local" \
    --health-interval 5s \
    --health-timeout 5s \
    --health-retries 5 \
    --health-start-period 10s \
    "$IMAGE_MINIO" \
    server /data --console-address ":9001" > /dev/null

print_success "$CONTAINER_MINIO container started"

print_info "Waiting for Kafka and MinIO..."
wait_for_healthy "$CONTAINER_KAFKA" 120
wait_for_healthy "$CONTAINER_MINIO" 60

# Create MinIO bucket
remove_container "$CONTAINER_MINIO_INIT"
print_info "Creating MinIO bucket..."
docker run --rm \
    --name "$CONTAINER_MINIO_INIT" \
    --network "$NETWORK_NAME" \
    "$IMAGE_MINIO_MC" \
    /bin/sh -c "mc alias set local http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} && mc mb --ignore-existing local/${SESSION_REPLAY_S3_BUCKET}" > /dev/null 2>&1

print_success "MinIO bucket '${SESSION_REPLAY_S3_BUCKET}' ready"

# Create Kafka topics
print_info "Creating Kafka topics..."
create_kafka_topics

# ── Phase 3: ClickHouse init + OTEL Collector ────────────────────────────
print_section "Phase 3: Initialising ClickHouse tables & OTEL Collector"

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
    -v "${ROOT_DIR}/backend/ingestion/clickhouse-session-replay-schema.sql:/init/clickhouse-session-replay-schema.sql:ro" \
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

# ── Phase 4: Session Replay Services ──────────────────────────────────────
print_section "Phase 4: Starting Session Replay Services"

remove_container "$CONTAINER_SESSION_CAPTURE"
print_info "Starting $CONTAINER_SESSION_CAPTURE ..."

docker run -d \
    --name "$CONTAINER_SESSION_CAPTURE" \
    --network "$NETWORK_NAME" \
    --restart unless-stopped \
    -p 3400:3400 \
    -e PORT=3400 \
    -e KAFKA_BROKERS=kafka:9092 \
    -e KAFKA_TOPIC=session_recording_events \
    -e RUST_LOG=pulse_session_capture=info \
    --health-cmd "curl -f http://localhost:3400/healthcheck" \
    --health-interval 10s \
    --health-timeout 5s \
    --health-retries 3 \
    --health-start-period 10s \
    "$IMAGE_SESSION_CAPTURE" > /dev/null

print_success "$CONTAINER_SESSION_CAPTURE container started"

remove_container "$CONTAINER_SESSION_INGESTION"
print_info "Starting $CONTAINER_SESSION_INGESTION ..."

docker run -d \
    --name "$CONTAINER_SESSION_INGESTION" \
    --network "$NETWORK_NAME" \
    --restart unless-stopped \
    -e KAFKA_BROKERS=kafka:9092 \
    -e KAFKA_TOPIC=session_recording_events \
    -e KAFKA_METADATA_TOPIC=clickhouse_session_replay_events \
    -e KAFKA_GROUP_ID=session-replay-ingestion \
    -e S3_ENDPOINT=http://minio:9000 \
    -e "S3_BUCKET=${SESSION_REPLAY_S3_BUCKET}" \
    -e "S3_ACCESS_KEY_ID=${MINIO_ROOT_USER}" \
    -e "S3_SECRET_ACCESS_KEY=${MINIO_ROOT_PASSWORD}" \
    -e S3_REGION=us-east-1 \
    -e MAX_BATCH_SIZE_KB=102400 \
    -e MAX_BATCH_AGE_MS=10000 \
    "$IMAGE_SESSION_INGESTION" > /dev/null

print_success "$CONTAINER_SESSION_INGESTION container started"

print_info "Waiting for session capture service..."
wait_for_healthy "$CONTAINER_SESSION_CAPTURE" 60

# ── Phase 5: Pulse Server ────────────────────────────────────────────────
print_section "Phase 5: Starting Pulse Server"

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

# ── Phase 6: Pulse UI & Alerts Cron ──────────────────────────────────────
print_section "Phase 6: Starting Pulse UI & Alerts Cron"

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
    echo -e "  Session Capture:    ${GREEN}http://localhost:3400/s/${NC}"
    echo -e "  MySQL:              ${GREEN}localhost:3307${NC}"
    echo -e "  ClickHouse HTTP:    ${GREEN}http://localhost:8123${NC}"
    echo -e "  Kafka:              ${GREEN}localhost:9092${NC}"
    echo -e "  MinIO Console:      ${GREEN}http://localhost:9101${NC}"
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
