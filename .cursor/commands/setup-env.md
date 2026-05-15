Set up the local development environment for MCP servers.

1. Check if `.cursor/.env` exists. If not, copy from `.cursor/.env.example`
2. Remind the user to fill in their credentials (GitHub PAT, Atlassian API token)
3. Verify Docker is running (needed for GitHub and Atlassian MCPs)
4. Test that `npx` is available (needed for memory, sequential-thinking, selenium, etc.)
5. Print a summary of which MCP servers are ready vs need configuration
