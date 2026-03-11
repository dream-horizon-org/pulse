#!/usr/bin/env bash
# ============================================================
# Pulse AI Agent — One-Command Setup Script
# ============================================================
# Usage:
#   ./setup.sh          → Build & start the agent (Docker)
#   ./setup.sh stop     → Stop the agent
#   ./setup.sh restart  → Restart the agent
#   ./setup.sh logs     → Tail container logs
#   ./setup.sh clean    → Stop and remove containers + images
#
# Prerequisites: Docker Desktop (https://www.docker.com/products/docker-desktop)
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_banner() {
  echo ""
  echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║       🤖 Pulse AI Agent Setup        ║${NC}"
  echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
  echo ""
}

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

# ── Pre-flight checks ───────────────────────────────────────

check_docker() {
  if ! command -v docker &>/dev/null; then
    log_error "Docker is not installed."
    echo ""
    echo "  Install Docker Desktop from:"
    echo "    https://www.docker.com/products/docker-desktop"
    echo ""
    exit 1
  fi

  if ! docker info &>/dev/null 2>&1; then
    log_error "Docker daemon is not running. Please start Docker Desktop."
    exit 1
  fi

  log_info "Docker is available ✓"
}

check_env_file() {
  if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
      log_warn ".env file not found. Creating from .env.example ..."
      cp .env.example .env
      echo ""
      log_error "Please edit pulse_ai/.env and set your GOOGLE_API_KEY"
      echo ""
      echo "  Get your API key from: https://aistudio.google.com/apikey"
      echo "  Then run this script again."
      echo ""
      exit 1
    else
      log_error ".env file not found and no .env.example available."
      exit 1
    fi
  fi

  log_info ".env file configured ✓"
}

# ── Commands ─────────────────────────────────────────────────

cmd_start() {
  print_banner
  check_docker
  check_env_file

  log_info "Building and starting Pulse AI Agent ..."
  echo ""

  docker compose up --build -d

  echo ""
  log_info "Pulse AI Agent is running! 🚀"
  echo ""
  echo -e "  ${CYAN}Web UI:${NC}  http://localhost:8000"
  echo -e "  ${CYAN}Logs:${NC}    cd pulse_ai && ./setup.sh logs"
  echo -e "  ${CYAN}Stop:${NC}    cd pulse_ai && ./setup.sh stop"
  echo ""
}

cmd_stop() {
  log_info "Stopping Pulse AI Agent ..."
  docker compose down
  log_info "Agent stopped ✓"
}

cmd_restart() {
  log_info "Restarting Pulse AI Agent ..."
  docker compose down
  docker compose up --build -d
  log_info "Agent restarted ✓"
  echo ""
  echo -e "  ${CYAN}Web UI:${NC}  http://localhost:8000"
  echo ""
}

cmd_logs() {
  docker compose logs -f
}

cmd_clean() {
  log_info "Stopping and removing Pulse AI Agent containers and images ..."
  docker compose down --rmi local --volumes --remove-orphans
  log_info "Cleanup complete ✓"
}

# ── Main ─────────────────────────────────────────────────────

COMMAND="${1:-start}"

case "$COMMAND" in
  start)   cmd_start   ;;
  stop)    cmd_stop    ;;
  restart) cmd_restart ;;
  logs)    cmd_logs    ;;
  clean)   cmd_clean   ;;
  *)
    echo "Usage: ./setup.sh [start|stop|restart|logs|clean]"
    exit 1
    ;;
esac
