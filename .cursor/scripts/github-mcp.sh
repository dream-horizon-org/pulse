#!/bin/bash
# Loads credentials from .cursor/.env and launches the GitHub MCP server.
# Prefers Docker (official ghcr.io/github/github-mcp-server image); if the
# daemon is not available, falls back to npx (@modelcontextprotocol/server-github)
# so MCP works without Docker.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

if [ -z "$GITHUB_PERSONAL_ACCESS_TOKEN" ]; then
  echo "Error: GITHUB_PERSONAL_ACCESS_TOKEN not set. Copy .cursor/.env.example to .cursor/.env and fill in your token." >&2
  exit 1
fi

docker_available() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

if docker_available; then
  docker_args=( -i --rm -e "GITHUB_PERSONAL_ACCESS_TOKEN=$GITHUB_PERSONAL_ACCESS_TOKEN" )
  if [ -n "$GITHUB_HOST" ]; then
    docker_args+=( -e "GITHUB_HOST=$GITHUB_HOST" )
  fi
  exec docker run "${docker_args[@]}" ghcr.io/github/github-mcp-server
fi

if command -v npx >/dev/null 2>&1; then
  exec npx -y @modelcontextprotocol/server-github
fi

echo "Error: GitHub MCP needs either a running Docker daemon (for ghcr.io/github/github-mcp-server) or Node.js with npx (for @modelcontextprotocol/server-github)." >&2
exit 1
