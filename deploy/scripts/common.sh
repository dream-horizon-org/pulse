#!/bin/bash

# ============================================================================
# Pulse Observability - Common Library
# Shared constants, helpers, and utility functions for all deploy scripts.
# Source this file: source "$(dirname "$0")/common.sh"
# ============================================================================

# Strict mode
set -euo pipefail

# ---------------------------------------------------------------------------
# Path Variables
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$DEPLOY_DIR")"

# ---------------------------------------------------------------------------
# Constants -- Container names (match docker-compose service names)
# ---------------------------------------------------------------------------
CONTAINER_MYSQL="pulse-mysql"
CONTAINER_CLICKHOUSE="pulse-clickhouse"
CONTAINER_CLICKHOUSE_INIT="pulse-clickhouse-init"
CONTAINER_OTEL_COLLECTOR="pulse-otel-collector"
CONTAINER_UI="pulse-ui"
CONTAINER_SERVER="pulse-server"
CONTAINER_ALERTS_CRON="pulse-alerts-cron"

# Ordered list (start order)
ALL_CONTAINERS=(
    "$CONTAINER_MYSQL"
    "$CONTAINER_CLICKHOUSE"
    "$CONTAINER_CLICKHOUSE_INIT"
    "$CONTAINER_OTEL_COLLECTOR"
    "$CONTAINER_SERVER"
    "$CONTAINER_UI"
    "$CONTAINER_ALERTS_CRON"
)

# ---------------------------------------------------------------------------
# Constants -- Image references
# ---------------------------------------------------------------------------
IMAGE_MYSQL="mysql:8.0"
IMAGE_CLICKHOUSE="clickhouse/clickhouse-server:24.8"
IMAGE_OTEL_COLLECTOR="otel/opentelemetry-collector-contrib:0.137.0"

# Custom-built images (tagged :local to avoid confusion with registry)
IMAGE_UI="pulse-ui:local"
IMAGE_SERVER="pulse-server:local"
IMAGE_ALERTS_CRON="pulse-alerts-cron:local"

# ---------------------------------------------------------------------------
# Constants -- Network & Volumes
# ---------------------------------------------------------------------------
NETWORK_NAME="pulse-network"
VOLUME_MYSQL="pulse-mysql-data"
VOLUME_CLICKHOUSE="pulse-clickhouse-data"

# ---------------------------------------------------------------------------
# Colors
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
print_success() { echo -e "${GREEN}✓ $1${NC}"; }
print_error()   { echo -e "${RED}✗ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠ $1${NC}"; }
print_info()    { echo -e "${BLUE}ℹ $1${NC}"; }

print_section() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

# ---------------------------------------------------------------------------
# Compose detection -- sets COMPOSE_CMD (empty string if unavailable)
# ---------------------------------------------------------------------------
detect_compose() {
    COMPOSE_CMD=""
    if command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
    elif docker compose version &> /dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
    fi
}

# Helper: run compose command using whichever variant is available
run_compose() {
    if [ -z "${COMPOSE_CMD:-}" ]; then
        detect_compose
    fi
    if [ -n "$COMPOSE_CMD" ]; then
        $COMPOSE_CMD "$@"
    else
        print_error "docker-compose is not available"
        return 1
    fi
}

has_compose() {
    if [ -z "${COMPOSE_CMD:-}" ]; then
        detect_compose
    fi
    [ -n "$COMPOSE_CMD" ]
}

# Run compose detection once at source time
detect_compose

# ---------------------------------------------------------------------------
# load_env -- Source the .env file and apply defaults
# ---------------------------------------------------------------------------
load_env() {
    if [ -f "$DEPLOY_DIR/.env" ]; then
        # Export all variables from .env
        set -a
        # shellcheck disable=SC1091
        source "$DEPLOY_DIR/.env"
        set +a
    fi

    # Apply defaults matching docker-compose.yml

    # MySQL
    export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-pulse_root_password}"
    export MYSQL_DATABASE="${MYSQL_DATABASE:-pulse_db}"
    export MYSQL_USER="${MYSQL_USER:-pulse_user}"
    export MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
    export MYSQL_WRITER_MAX_POOL_SIZE="${MYSQL_WRITER_MAX_POOL_SIZE:-10}"
    export MYSQL_READER_MAX_POOL_SIZE="${MYSQL_READER_MAX_POOL_SIZE:-10}"

    # ClickHouse / OTEL
    export OTEL_CLICKHOUSE_DATABASE="${OTEL_CLICKHOUSE_DATABASE:-otel}"
    export OTEL_CLICKHOUSE_USER="${OTEL_CLICKHOUSE_USER:-pulse_user}"
    export OTEL_CLICKHOUSE_PASSWORD="${OTEL_CLICKHOUSE_PASSWORD:-pulse_password}"
    export GOOGLE_OAUTH_ENABLED="${GOOGLE_OAUTH_ENABLED:-false}"
    export REACT_APP_GOOGLE_CLIENT_ID="${REACT_APP_GOOGLE_CLIENT_ID:-}"
    export REACT_APP_PULSE_SERVER_URL="${REACT_APP_PULSE_SERVER_URL:-}"
    export REACT_APP_GOOGLE_OAUTH_ENABLED="${REACT_APP_GOOGLE_OAUTH_ENABLED:-${GOOGLE_OAUTH_ENABLED}}"
    export CONFIG_SERVICE_APPLICATION_CRONMANAGERBASEURL="${CONFIG_SERVICE_APPLICATION_CRONMANAGERBASEURL:-http://pulse-alerts-cron:4000/cron}"
    export CONFIG_SERVICE_APPLICATION_SERVICEURL="${CONFIG_SERVICE_APPLICATION_SERVICEURL:-http://pulse-server:8080}"
    export CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHCLIENTID="${CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHCLIENTID:-}"
    export CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHENABLED="${CONFIG_SERVICE_APPLICATION_GOOGLEOAUTHENABLED:-${GOOGLE_OAUTH_ENABLED}}"
    export CONFIG_SERVICE_APPLICATION_JWTSECRET="${CONFIG_SERVICE_APPLICATION_JWTSECRET:-dev-secret-key-at-least-32-characters-long-for-local-testing-only}"
    export CONFIG_SERVICE_APPLICATION_WEBHOOKURL="${CONFIG_SERVICE_APPLICATION_WEBHOOKURL:-}"
    export VAULT_ENCRYPTION_MASTER_KEY="${VAULT_ENCRYPTION_MASTER_KEY:-}"
    export CONFIG_S3_BUCKET_NAME="${CONFIG_S3_BUCKET_NAME:-pulse-config}"
    export CONFIG_DETAILS_S3_FILE_PATH="${CONFIG_DETAILS_S3_FILE_PATH:-config/pulse-config.json}"
    export INTERACTION_DETAILS_S3_FILE_PATH="${INTERACTION_DETAILS_S3_FILE_PATH:-config/interaction-config.json}"
    export CONFIG_CLOUDFRONT_DISTRIBUTION_ID="${CONFIG_CLOUDFRONT_DISTRIBUTION_ID:-NONE}"
    export CONFIG_CLOUDFRONT_ASSET_PATH="${CONFIG_CLOUDFRONT_ASSET_PATH:-/config/pulse-config.json}"
    export INTERACTION_CLOUDFRONT_ASSET_PATH="${INTERACTION_CLOUDFRONT_ASSET_PATH:-/config/interaction-config.json}"
    export INTERACTION_CONFIG_URL="${INTERACTION_CONFIG_URL:-}"
    export LOGS_COLLECTOR_URL="${LOGS_COLLECTOR_URL:-}"
    export METRIC_COLLECTOR_URL="${METRIC_COLLECTOR_URL:-}"
    export SPAN_COLLECTOR_URL="${SPAN_COLLECTOR_URL:-}"
    export CUSTOM_EVENT_COLLECTOR_URL="${CUSTOM_EVENT_COLLECTOR_URL:-}"
    export CONFIG_SERVICE_APPLICATION_QUERY_ENGINE="${CONFIG_SERVICE_APPLICATION_QUERY_ENGINE:-athena}"
    export CONFIG_SERVICE_APPLICATION_ATHENA_REGION="${CONFIG_SERVICE_APPLICATION_ATHENA_REGION:-ap-south-1}"
    export CONFIG_SERVICE_APPLICATION_ATHENA_DATABASE="${CONFIG_SERVICE_APPLICATION_ATHENA_DATABASE:-pulse_athena_db}"
    export CONFIG_SERVICE_APPLICATION_ATHENA_OUTPUT_LOCATION="${CONFIG_SERVICE_APPLICATION_ATHENA_OUTPUT_LOCATION:-s3://puls-otel-config/}"
    export CONFIG_SERVICE_APPLICATION_GCP_PROJECT_ID="${CONFIG_SERVICE_APPLICATION_GCP_PROJECT_ID:-}"
    export CONFIG_SERVICE_APPLICATION_PULSESERVERURL="${CONFIG_SERVICE_APPLICATION_PULSESERVERURL:-http://pulse-server:8080}"
    export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-}"
    export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-}"
    export AWS_SESSION_TOKEN="${AWS_SESSION_TOKEN:-}"
}

# ---------------------------------------------------------------------------
# wait_for_healthy -- Poll container health until healthy or timeout
# ---------------------------------------------------------------------------
wait_for_healthy() {
    local container="$1"
    local timeout="${2:-120}"
    local interval=5
    local elapsed=0

    while [ "$elapsed" -lt "$timeout" ]; do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "not_found")

        if [ "$status" = "healthy" ]; then
            print_success "$container is healthy"
            return 0
        fi

        local running
        running=$(docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null || echo "false")
        if [ "$running" = "false" ] && [ "$status" != "starting" ]; then
            print_error "$container is not running (status: $status)"
            return 1
        fi

        echo -ne "\r  Waiting for $container ... $status (${elapsed}s/${timeout}s)  "
        sleep "$interval"
        elapsed=$((elapsed + interval))
    done

    echo ""
    print_error "$container did not become healthy within ${timeout}s"
    return 1
}

# ---------------------------------------------------------------------------
# verify_mysql_init -- Wait for MySQL init scripts to finish, then check
#                       container logs for errors and verify table count.
#                       Returns non-zero on failure.
#
#   MySQL's healthcheck (mysqladmin ping) can pass while init scripts are
#   still running on the temporary server, so we poll for tables before
#   declaring success or failure.
# ---------------------------------------------------------------------------
verify_mysql_init() {
    local table_count retries=0 max_retries=12

    while [ "$retries" -lt "$max_retries" ]; do
        table_count=$(docker exec "$CONTAINER_MYSQL" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" -sNe \
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${MYSQL_DATABASE}';" 2>/dev/null || echo "0")

        if [ "$table_count" -gt 0 ] 2>/dev/null; then
            break
        fi

        retries=$((retries + 1))
        echo -ne "\r  Waiting for MySQL init to complete... (${retries}/${max_retries})  "
        sleep 5
    done
    [ "$retries" -gt 0 ] && echo ""

    local init_errors
    init_errors=$(docker logs "$CONTAINER_MYSQL" 2>&1 | grep -E "^ERROR " || true)
    if [ -n "$init_errors" ]; then
        print_error "MySQL init script failed:"
        echo "$init_errors" | while IFS= read -r line; do
            echo -e "    ${RED}$line${NC}"
        done
        return 1
    fi

    if [ "$table_count" -eq 0 ] 2>/dev/null; then
        print_error "MySQL init did not create any tables (timed out after $((max_retries * 5))s)"
        print_info "Check logs: docker logs $CONTAINER_MYSQL"
        return 1
    fi

    print_success "MySQL initialized ($table_count tables, no errors)"
    return 0
}

# ---------------------------------------------------------------------------
# verify_clickhouse_init -- Verify ClickHouse tables were created.
#                            Returns non-zero on failure.
# ---------------------------------------------------------------------------
verify_clickhouse_init() {
    local table_count
    table_count=$(docker exec "$CONTAINER_CLICKHOUSE" clickhouse-client --query \
        "SELECT count() FROM system.tables WHERE database = '${OTEL_CLICKHOUSE_DATABASE}'" 2>/dev/null || echo "0")

    if [ "$table_count" -eq 0 ] 2>/dev/null; then
        print_error "ClickHouse init failed: no tables in '${OTEL_CLICKHOUSE_DATABASE}' database"
        print_info "Check logs: docker logs $CONTAINER_CLICKHOUSE"
        return 1
    fi

    print_success "ClickHouse initialized ($table_count tables)"
    return 0
}

# ---------------------------------------------------------------------------
# ensure_network -- Create the Docker bridge network if it doesn't exist
# ---------------------------------------------------------------------------
ensure_network() {
    if ! docker network inspect "$NETWORK_NAME" > /dev/null 2>&1; then
        print_info "Creating Docker network: $NETWORK_NAME"
        docker network create "$NETWORK_NAME" --driver bridge > /dev/null
        print_success "Network $NETWORK_NAME created"
    else
        print_success "Network $NETWORK_NAME already exists"
    fi
}

# ---------------------------------------------------------------------------
# ensure_volumes -- Create named volumes if they don't exist
# ---------------------------------------------------------------------------
ensure_volumes() {
    for vol in "$VOLUME_MYSQL" "$VOLUME_CLICKHOUSE"; do
        if ! docker volume inspect "$vol" > /dev/null 2>&1; then
            print_info "Creating Docker volume: $vol"
            docker volume create "$vol" > /dev/null
            print_success "Volume $vol created"
        else
            print_success "Volume $vol already exists"
        fi
    done
}

# ---------------------------------------------------------------------------
# remove_container -- Force-remove a container if it exists (idempotent)
# ---------------------------------------------------------------------------
remove_container() {
    local name="$1"
    docker rm -f "$name" > /dev/null 2>&1 || true
}

# ---------------------------------------------------------------------------
# configure_docker_host -- Set DOCKER_HOST for the current session and persist
#                           it in the user's shell profile.
# ---------------------------------------------------------------------------
configure_docker_host() {
    local colima_sock="unix://${HOME}/.colima/default/docker.sock"

    export DOCKER_HOST="$colima_sock"
    print_success "DOCKER_HOST set to $colima_sock (current session)"

    local shell_profile=""
    if [ -f "$HOME/.zshrc" ]; then
        shell_profile="$HOME/.zshrc"
    elif [ -f "$HOME/.bash_profile" ]; then
        shell_profile="$HOME/.bash_profile"
    elif [ -f "$HOME/.bashrc" ]; then
        shell_profile="$HOME/.bashrc"
    else
        shell_profile="$HOME/.zshrc"
    fi

    local export_line="export DOCKER_HOST=\"unix://\${HOME}/.colima/default/docker.sock\""

    if ! grep -q '^[[:space:]]*export DOCKER_HOST=' "$shell_profile" 2>/dev/null; then
        echo "" >> "$shell_profile"
        echo "# Docker Engine via Colima (added by Pulse quickstart)" >> "$shell_profile"
        echo "$export_line" >> "$shell_profile"
        print_success "Added DOCKER_HOST to $shell_profile (takes effect in new terminals)"
    else
        print_info "DOCKER_HOST already configured in $shell_profile"
    fi

    # Fix Docker credential store: a stale credsStore value of "desktop" may
    # exist from a previous installation.  That credential helper binary does
    # not exist under Colima and breaks all image pulls -- clear it.
    local docker_cfg="$HOME/.docker/config.json"
    if [ -f "$docker_cfg" ] && grep -q '"credsStore"[[:space:]]*:[[:space:]]*"desktop"' "$docker_cfg" 2>/dev/null; then
        if command -v python3 &> /dev/null; then
            python3 -c "
import json, sys
with open('$docker_cfg') as f:
    cfg = json.load(f)
cfg['credsStore'] = ''
with open('$docker_cfg', 'w') as f:
    json.dump(cfg, f, indent=2)
" 2>/dev/null && print_success "Fixed Docker credential store (cleared stale 'desktop' helper)" \
               || print_warning "Could not patch $docker_cfg -- if pulls fail, manually set credsStore to \"\""
        elif command -v sed &> /dev/null; then
            sed -i.bak 's/"credsStore"[[:space:]]*:[[:space:]]*"desktop"/"credsStore": ""/' "$docker_cfg" 2>/dev/null \
                && print_success "Fixed Docker credential store (cleared stale 'desktop' helper)" \
                || print_warning "Could not patch $docker_cfg -- if pulls fail, manually set credsStore to \"\""
        fi
    fi
}

# ---------------------------------------------------------------------------
# install_docker -- Install Docker Engine (+ Colima on macOS).
#
#   - On macOS we install the Docker CLI and Colima (open-source, lightweight
#     container runtime).
#   - On Linux we install Docker Engine CE via the system package manager.
# ---------------------------------------------------------------------------
install_docker() {
    local os
    os="$(uname -s)"

    case "$os" in
        Linux)
            if command -v apt-get &> /dev/null; then
                print_info "Detected Debian/Ubuntu -- installing Docker Engine via apt..."
                sudo apt-get update -y
                sudo apt-get install -y ca-certificates curl gnupg
                sudo install -m 0755 -d /etc/apt/keyrings
                curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null || \
                curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null
                sudo chmod a+r /etc/apt/keyrings/docker.gpg
                local codename distro_id
                codename="$(. /etc/os-release && echo "$VERSION_CODENAME" 2>/dev/null || echo "")"
                distro_id="$(. /etc/os-release && echo "$ID" 2>/dev/null || echo "ubuntu")"
                echo \
                    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${distro_id} ${codename} stable" | \
                    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
                sudo apt-get update -y
                sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            elif command -v yum &> /dev/null; then
                print_info "Detected RHEL/CentOS -- installing Docker Engine via yum..."
                sudo yum install -y yum-utils
                sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo 2>/dev/null || \
                sudo yum-config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo 2>/dev/null
                sudo yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
                sudo systemctl start docker
                sudo systemctl enable docker
            elif command -v dnf &> /dev/null; then
                print_info "Detected Fedora -- installing Docker Engine via dnf..."
                sudo dnf -y install dnf-plugins-core
                sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
                sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
                sudo systemctl start docker
                sudo systemctl enable docker
            elif command -v pacman &> /dev/null; then
                print_info "Detected Arch Linux -- installing Docker Engine via pacman..."
                sudo pacman -Sy --noconfirm docker
                sudo systemctl start docker
                sudo systemctl enable docker
            else
                print_error "Unsupported Linux distribution. Please install Docker Engine manually:"
                echo "  https://docs.docker.com/engine/install/"
                return 1
            fi
            if ! groups | grep -q docker; then
                sudo usermod -aG docker "$USER" 2>/dev/null || true
                print_warning "Added $USER to the docker group. You may need to log out and back in."
            fi
            ;;
        Darwin)
            if ! command -v brew &> /dev/null; then
                print_error "Homebrew is required on macOS. Install it first:"
                echo '  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
                return 1
            fi
            print_info "Installing Docker CLI (client)..."
            brew install docker
            print_info "Installing Colima (container runtime)..."
            brew install colima
            print_info "Starting Colima VM (4 CPUs, 8 GB RAM, 60 GB disk)..."
            if ! _start_colima_with_retry 4 8 60; then
                return 1
            fi
            print_info "Useful Colima commands:"
            echo "    colima start          # start the VM"
            echo "    colima stop           # stop the VM"
            echo "    colima status         # check status"
            ;;
        *)
            print_error "Unsupported OS ($os). Please install Docker Engine manually:"
            echo "  https://docs.docker.com/engine/install/"
            return 1
            ;;
    esac
}

# ---------------------------------------------------------------------------
# start_docker_daemon -- Attempt to start the Docker daemon if it is not
#                         running. On macOS this means starting Colima.
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# _wait_for_docker -- Retry `docker info` with a short timeout.
#                      Useful after starting a VM where the daemon needs a
#                      few seconds to initialise.
# ---------------------------------------------------------------------------
_wait_for_docker() {
    local timeout="${1:-60}"
    local interval=3
    local elapsed=0

    # On macOS with Colima, wait for the socket file to appear first.
    # The daemon can't respond until the socket is created.
    if [ "$(uname -s)" = "Darwin" ]; then
        local sock_path="${HOME}/.colima/default/docker.sock"
        while [ "$elapsed" -lt "$timeout" ] && [ ! -S "$sock_path" ]; do
            echo -ne "\r  Waiting for Colima socket to appear ... (${elapsed}s/${timeout}s)  "
            sleep "$interval"
            elapsed=$((elapsed + interval))
        done
        if [ ! -S "$sock_path" ]; then
            echo ""
            print_warning "Colima socket not found at $sock_path after ${elapsed}s"
            return 1
        fi
    fi

    while [ "$elapsed" -lt "$timeout" ]; do
        if docker info > /dev/null 2>&1; then
            echo ""
            return 0
        fi
        echo -ne "\r  Waiting for Docker daemon ... (${elapsed}s/${timeout}s)  "
        sleep "$interval"
        elapsed=$((elapsed + interval))
    done
    echo ""
    return 1
}

# ---------------------------------------------------------------------------
# _start_colima_with_retry -- Start Colima and wait for Docker daemon.
#   If the first attempt fails (daemon not responding), assume stale state,
#   force-stop Colima, and retry once.  This handles the common case where a
#   prior Colima session was not cleanly shut down.
#
#   Args: cpu memory disk  (positional, all optional -- defaults 4 / 8 / 60)
# ---------------------------------------------------------------------------
_start_colima_with_retry() {
    local cpu="${1:-4}" mem="${2:-8}" disk="${3:-60}"
    local max_attempts=2
    local docker_timeout=120

    for attempt in $(seq 1 "$max_attempts"); do
        if [ "$attempt" -gt 1 ]; then
            print_warning "Docker daemon did not respond -- cleaning up stale Colima state (attempt $attempt/$max_attempts)..."
            colima stop --force 2>/dev/null || true
            sleep 3
        fi

        # Start (or restart) Colima
        if ! colima start --cpu "$cpu" --memory "$mem" --disk "$disk"; then
            if [ "$attempt" -lt "$max_attempts" ]; then
                continue
            fi
            print_error "Failed to start Colima after $max_attempts attempts"
            return 1
        fi

        # Verify Colima reports itself as running
        if ! colima status 2>&1 | grep -q "is running"; then
            if [ "$attempt" -lt "$max_attempts" ]; then
                print_warning "Colima does not report as running after start"
                continue
            fi
            print_error "Colima did not reach a running state"
            return 1
        fi

        configure_docker_host

        print_info "Waiting for Docker daemon to be ready (timeout: ${docker_timeout}s)..."
        if _wait_for_docker "$docker_timeout"; then
            print_success "Colima is running -- Docker Engine Community is ready"
            return 0
        fi

        # If this is the last attempt, don't silently loop
        if [ "$attempt" -ge "$max_attempts" ]; then
            break
        fi
    done

    print_error "Colima started but Docker daemon is not responding after ${docker_timeout}s"
    echo "  Diagnostic commands:"
    echo "    colima status"
    echo "    colima ssh -- systemctl status docker"
    echo "  Manual recovery:"
    echo "    colima stop --force && colima delete --force"
    echo "    colima start --cpu $cpu --memory $mem --disk $disk"
    echo "    export DOCKER_HOST=unix://\$HOME/.colima/default/docker.sock"
    echo "    docker info"
    return 1
}

# ---------------------------------------------------------------------------
# _ensure_docker_host_on_mac -- On macOS, if DOCKER_HOST is not set and
#                                 the Colima socket exists, auto-configure it.
#                                 This avoids false "daemon not running" when
#                                 Colima is running but the env var is missing.
# ---------------------------------------------------------------------------
_ensure_docker_host_on_mac() {
    [ "$(uname -s)" = "Darwin" ] || return 0

    # Already set and working? Nothing to do.
    if [ -n "${DOCKER_HOST:-}" ] && docker info > /dev/null 2>&1; then
        return 0
    fi

    # If DOCKER_HOST points to a non-existent socket (e.g., stale Docker Desktop
    # reference), clear it so we can try the Colima socket instead.
    if [ -n "${DOCKER_HOST:-}" ]; then
        local current_sock="${DOCKER_HOST#unix://}"
        if [ ! -S "$current_sock" ]; then
            print_warning "Current DOCKER_HOST socket does not exist ($DOCKER_HOST) -- trying Colima socket"
            unset DOCKER_HOST
        fi
    fi

    # Check if the Colima socket exists (Colima running, env var just missing)
    local colima_sock="unix://${HOME}/.colima/default/docker.sock"
    if [ -S "${HOME}/.colima/default/docker.sock" ]; then
        export DOCKER_HOST="$colima_sock"
        if docker info > /dev/null 2>&1; then
            print_info "Auto-detected Colima socket (DOCKER_HOST=$colima_sock)"
            return 0
        fi
    fi

    # Best-effort only -- return 0 so set -e does not abort the script.
    # The caller will proceed to the daemon check which handles the failure.
    return 0
}

start_docker_daemon() {
    local os
    os="$(uname -s)"

    case "$os" in
        Linux)
            if command -v systemctl &> /dev/null; then
                print_info "Attempting to start Docker daemon via systemctl..."
                sudo systemctl start docker 2>/dev/null || true
            elif command -v service &> /dev/null; then
                print_info "Attempting to start Docker daemon via service..."
                sudo service docker start 2>/dev/null || true
            else
                print_info "Attempting to start Docker daemon directly..."
                sudo dockerd &>/dev/null &
                disown 2>/dev/null || true
            fi
            # Give the daemon a moment, then verify
            if _wait_for_docker 30; then
                return 0
            fi
            print_error "Could not start Docker daemon."
            echo "  Try one of:"
            echo "    sudo systemctl start docker"
            echo "    sudo service docker start"
            echo "    sudo dockerd"
            return 1
            ;;
        Darwin)
            if command -v colima &> /dev/null; then
                # If Colima is already running AND Docker responds, nothing to do.
                if colima status 2>&1 | grep -q "is running"; then
                    configure_docker_host
                    if docker info > /dev/null 2>&1; then
                        print_success "Colima is already running and Docker daemon is responsive"
                        return 0
                    fi
                    # Colima says it's running but Docker is unresponsive -- stale state
                    print_warning "Colima reports running but Docker daemon is not responding -- will restart"
                fi

                print_info "Starting Colima VM (4 CPUs, 8 GB RAM, 60 GB disk)..."
                if _start_colima_with_retry 4 8 60; then
                    return 0
                fi
                return 1
            fi

            # Colima is not installed -- offer to install it
            print_warning "Colima (container runtime) is not installed."
            if command -v brew &> /dev/null; then
                echo ""
                read -p "Would you like to install Colima now? (yes/no): " -r
                echo ""
                if [[ $REPLY =~ ^[Yy]([Ee][Ss])?$ ]]; then
                    print_info "Installing Colima..."
                    brew install colima
                    print_info "Starting Colima VM (4 CPUs, 8 GB RAM, 60 GB disk)..."
                    if _start_colima_with_retry 4 8 60; then
                        return 0
                    fi
                    return 1
                fi
            fi

            print_error "Colima is required on macOS. Install it with:"
            echo "    brew install colima && colima start --cpu 4 --memory 8 --disk 60"
            return 1
            ;;
        *)
            print_error "Cannot auto-start Docker on this OS. Please start it manually."
            return 1
            ;;
    esac
}

# ---------------------------------------------------------------------------
# check_docker -- Verify Docker is installed and the daemon is running.
#                  Offers to install Docker if it is missing.
#                  Offers to start the daemon if it is stopped.
# ---------------------------------------------------------------------------
check_docker() {
    # ── 1. Check Docker CLI ────────────────────────────────────────────────
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        echo ""
        read -p "Would you like to install Docker now? (yes/no): " -r
        echo ""
        if [[ $REPLY =~ ^[Yy]([Ee][Ss])?$ ]]; then
            install_docker
            if ! command -v docker &> /dev/null; then
                print_error "Docker installation failed. Please install manually:"
                echo "  https://docs.docker.com/engine/install/"
                exit 1
            fi
            print_success "Docker installed successfully ($(docker --version))"
            # Re-detect compose after installation
            detect_compose
        else
            echo "Please install Docker Engine: https://docs.docker.com/engine/install/"
            exit 1
        fi
    else
        print_success "Docker is installed ($(docker --version))"
    fi

    # ── 2. On macOS, auto-detect Colima socket before checking daemon ──────
    _ensure_docker_host_on_mac

    # ── 3. Check Docker daemon ─────────────────────────────────────────────
    if ! docker info > /dev/null 2>&1; then
        print_warning "Docker daemon is not running"
        echo ""
        read -p "Would you like to start it now? (yes/no): " -r
        echo ""
        if [[ $REPLY =~ ^[Yy]([Ee][Ss])?$ ]]; then
            start_docker_daemon
            if ! docker info > /dev/null 2>&1; then
                print_error "Docker daemon could not be started"
                exit 1
            fi
            print_success "Docker daemon is running"
        else
            echo "Please start Docker and try again"
            exit 1
        fi
    else
        print_success "Docker daemon is running"
    fi
}
