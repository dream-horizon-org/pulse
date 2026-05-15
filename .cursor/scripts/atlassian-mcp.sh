#!/bin/bash
# Loads credentials from .cursor/.env and launches the Atlassian MCP server

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

if [ -z "$JIRA_API_TOKEN" ]; then
  echo "Error: JIRA_API_TOKEN not set. Copy .cursor/.env.example to .cursor/.env and fill in your credentials." >&2
  exit 1
fi

exec docker run -i --rm \
  -e CONFLUENCE_URL="$CONFLUENCE_URL" \
  -e CONFLUENCE_USERNAME="$CONFLUENCE_USERNAME" \
  -e CONFLUENCE_API_TOKEN="$CONFLUENCE_API_TOKEN" \
  -e JIRA_URL="$JIRA_URL" \
  -e JIRA_USERNAME="$JIRA_USERNAME" \
  -e JIRA_API_TOKEN="$JIRA_API_TOKEN" \
  ghcr.io/sooperset/mcp-atlassian:latest
