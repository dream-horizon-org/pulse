#!/bin/bash

# ============================================================================
# Pulse Observability - Build Script
# Builds Docker images for pulse-ui, pulse-server, and pulse-alerts-cron.
# Uses Docker Compose if available, otherwise falls back to Docker CLI.
#
# Usage:
#   ./build.sh [--no-cache] [ui|server|cron|all]
#
# Examples:
#   ./build.sh              # Build all images
#   ./build.sh ui           # Build pulse-ui only
#   ./build.sh --no-cache   # Build all without cache
# ============================================================================

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Pulse Observability - Build Script       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# ── Pre-flight checks ──────────────────────────────────────────────────────
check_docker
load_env

# ── Ensure BuildKit + buildx are available ─────────────────────────────────
setup_buildx() {
    local plugins_dir="$HOME/.docker/cli-plugins"

    if docker buildx version &> /dev/null 2>&1; then
        return 0
    fi

    if ! command -v docker-buildx &> /dev/null && ! [ -x "$plugins_dir/docker-buildx" ]; then
        print_info "Installing docker-buildx plugin..."
        if command -v brew &> /dev/null; then
            brew install docker-buildx 2>/dev/null || true
        elif command -v apt-get &> /dev/null; then
            sudo apt-get install -y docker-buildx-plugin 2>/dev/null || true
        fi
    fi

    if command -v brew &> /dev/null; then
        local buildx_bin
        buildx_bin="$(brew --prefix)/opt/docker-buildx/bin/docker-buildx"
        if [ -x "$buildx_bin" ] && ! [ -x "$plugins_dir/docker-buildx" ]; then
            mkdir -p "$plugins_dir"
            ln -sfn "$buildx_bin" "$plugins_dir/docker-buildx"
            print_success "Linked docker-buildx into $plugins_dir"
        fi
    fi

    if docker buildx version &> /dev/null 2>&1; then
        return 0
    fi

    print_warning "docker-buildx not available; falling back to legacy builder"
    return 1
}

if setup_buildx; then
    export DOCKER_BUILDKIT=1
else
    export DOCKER_BUILDKIT=0
fi

# ── Parse arguments ────────────────────────────────────────────────────────
NO_CACHE=""
SERVICES=()

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        ui|pulse-ui)
            SERVICES+=("ui")
            shift
            ;;
        server|pulse-server)
            SERVICES+=("server")
            shift
            ;;
        cron|alerts-cron|pulse-alerts-cron)
            SERVICES+=("cron")
            shift
            ;;
        all)
            SERVICES=()
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--no-cache] [ui|server|cron|all]"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: $0 [--no-cache] [ui|server|cron|all]"
            exit 1
            ;;
    esac
done

# Default: build everything
if [ ${#SERVICES[@]} -eq 0 ]; then
    SERVICES=("ui" "server" "cron")
fi

# ── Compose path (simple) ────────────────────────────────────────────────
if has_compose; then
    cd "$DEPLOY_DIR"

    # Map service names to compose service names
    COMPOSE_SERVICES=""
    for svc in "${SERVICES[@]}"; do
        case $svc in
            ui)     COMPOSE_SERVICES="$COMPOSE_SERVICES pulse-ui" ;;
            server) COMPOSE_SERVICES="$COMPOSE_SERVICES pulse-server" ;;
            cron)   COMPOSE_SERVICES="$COMPOSE_SERVICES pulse-alerts-cron" ;;
        esac
    done

    print_info "Building via Docker Compose..."
    # shellcheck disable=SC2086
    if run_compose build $NO_CACHE --parallel $COMPOSE_SERVICES; then
        echo ""
        echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║   Build completed successfully!            ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
        echo ""
        print_info "Next: ./start.sh to start all services"
    else
        echo ""
        echo -e "${RED}╔════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║   Build failed!                            ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════╝${NC}"
        exit 1
    fi
    exit 0
fi

# ── Docker CLI path (no compose) ──────────────────────────────────────────
print_info "Building via Docker CLI..."

build_ui() {
    print_info "Building pulse-ui image..."
    docker build \
        $NO_CACHE \
        -t "$IMAGE_UI" \
        --build-arg "REACT_APP_GOOGLE_CLIENT_ID=${REACT_APP_GOOGLE_CLIENT_ID}" \
        --build-arg "REACT_APP_PULSE_SERVER_URL=${REACT_APP_PULSE_SERVER_URL}" \
        --build-arg "REACT_APP_GOOGLE_OAUTH_ENABLED=${REACT_APP_GOOGLE_OAUTH_ENABLED}" \
        -f "$ROOT_DIR/pulse-ui/Dockerfile" \
        "$ROOT_DIR/pulse-ui"
    print_success "pulse-ui image built -> $IMAGE_UI"
}

build_server() {
    print_info "Building pulse-server image..."
    docker build \
        $NO_CACHE \
        -t "$IMAGE_SERVER" \
        -f "$ROOT_DIR/backend/server/Dockerfile" \
        "$ROOT_DIR/backend/server"
    print_success "pulse-server image built -> $IMAGE_SERVER"
}

build_cron() {
    print_info "Building pulse-alerts-cron image..."
    docker build \
        $NO_CACHE \
        -t "$IMAGE_ALERTS_CRON" \
        -f "$ROOT_DIR/backend/pulse-alerts-cron/Dockerfile" \
        "$ROOT_DIR/backend/pulse-alerts-cron"
    print_success "pulse-alerts-cron image built -> $IMAGE_ALERTS_CRON"
}

# When building multiple images, run them in parallel with per-service log files
BUILD_LOG_DIR=$(mktemp -d)
PIDS=()
NAMES=()
FAILED=0

for svc in "${SERVICES[@]}"; do
    if [ ${#SERVICES[@]} -gt 1 ]; then
        local_log="${BUILD_LOG_DIR}/${svc}.log"
        case $svc in
            ui)     build_ui     > "$local_log" 2>&1 & PIDS+=($!); NAMES+=("ui")     ;;
            server) build_server > "$local_log" 2>&1 & PIDS+=($!); NAMES+=("server") ;;
            cron)   build_cron   > "$local_log" 2>&1 & PIDS+=($!); NAMES+=("cron")   ;;
        esac
    else
        case $svc in
            ui)     build_ui     || FAILED=1 ;;
            server) build_server || FAILED=1 ;;
            cron)   build_cron   || FAILED=1 ;;
        esac
    fi
done

if [ ${#PIDS[@]} -gt 0 ]; then
    print_info "Building ${#PIDS[@]} images in parallel..."
    print_info "Build logs: ${BUILD_LOG_DIR}/"
    for i in "${!PIDS[@]}"; do
        if wait "${PIDS[$i]}"; then
            print_success "${NAMES[$i]} build succeeded"
        else
            print_error "${NAMES[$i]} build failed (log: ${BUILD_LOG_DIR}/${NAMES[$i]}.log)"
            echo ""
            echo "--- Last 30 lines of ${NAMES[$i]} build log ---"
            tail -30 "${BUILD_LOG_DIR}/${NAMES[$i]}.log" 2>/dev/null || true
            echo "--- end ---"
            echo ""
            FAILED=1
        fi
    done
fi

echo ""

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║   Build completed successfully!            ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
    echo ""
    print_info "Built images:"
    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep -E "^(REPOSITORY|pulse-)" || true
    echo ""
    print_info "Next: ./start.sh to start all services"
    rm -rf "$BUILD_LOG_DIR" 2>/dev/null || true
else
    echo -e "${RED}╔════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║   Build failed!                            ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════╝${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check build logs in: ${BUILD_LOG_DIR}/"
    echo "  2. Check Docker daemon is running"
    echo "  3. Ensure you have enough disk space"
    echo "  4. Try: $0 --no-cache"
    exit 1
fi
