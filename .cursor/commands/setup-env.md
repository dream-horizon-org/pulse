Set up the local development environment for MCP servers.

1. Check if `.cursor/.env` exists. If not, copy from `.cursor/.env.example`
2. Remind the user to fill in their credentials (GitHub PAT, Atlassian API token)
3. For GitHub / Atlassian MCPs: either start Docker (preferred; uses official images) or ensure **Node + npx** (GitHub fallback) and/or **uv** with `uvx` (Atlassian fallback) — see `.cursor/scripts/github-mcp.sh` and `atlassian-mcp.sh`
4. Test that `npx` is available (needed for memory, sequential-thinking, selenium, etc.)
5. Print a summary of which MCP servers are ready vs need configuration
