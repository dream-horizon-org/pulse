#!/bin/bash
# Loads credentials from .cursor/.env and launches the Atlassian MCP server.
# Prefers Docker (ghcr.io/sooperset/mcp-atlassian); if Docker is unavailable,
# falls back to uvx (PyPI mcp-atlassian) per upstream docs.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

if [ -z "$JIRA_API_TOKEN" ]; then
  echo "Error: JIRA_API_TOKEN not set. Copy .cursor/.env.example to .cursor/.env and fill in your credentials." >&2
  exit 1
fi

docker_available() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

if docker_available; then
  exec docker run -i --rm \
    -e CONFLUENCE_URL="$CONFLUENCE_URL" \
    -e CONFLUENCE_USERNAME="$CONFLUENCE_USERNAME" \
    -e CONFLUENCE_API_TOKEN="$CONFLUENCE_API_TOKEN" \
    -e JIRA_URL="$JIRA_URL" \
    -e JIRA_USERNAME="$JIRA_USERNAME" \
    -e JIRA_API_TOKEN="$JIRA_API_TOKEN" \
    ghcr.io/sooperset/mcp-atlassian:latest
fi

if command -v uvx >/dev/null 2>&1; then
  exec uvx mcp-atlassian
fi

echo "Error: Atlassian MCP needs either a running Docker daemon (for ghcr.io/sooperset/mcp-atlassian) or uv with uvx (install from https://docs.astral.sh/uv/)." >&2
exit 1
