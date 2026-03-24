Set up and start the full Pulse stack for a new developer.

1. Check prerequisites: Docker running, `node` (>=18), `yarn`, `java` (17), `mvn`, `python` (>=3.10)
2. Set up env files:
   - `deploy/.env`: copy from `deploy/.env.example` if missing
   - `pulse_ai/.env`: copy from `.env.example` if missing
   - `pulse-ui/.env`: copy from `.env.example` if missing
3. Build services: `cd deploy && ./scripts/build.sh all` (includes pulse-ai-agent by default)
4. Start services: `cd deploy && ./scripts/start.sh -d`
5. Read credentials from `deploy/.env` for health check commands
6. Run `docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"` to discover actual ports
7. Wait for health checks (use ports from step 6):
   - MySQL: `docker exec pulse-mysql mysqladmin ping -h localhost`
   - ClickHouse: `docker exec pulse-clickhouse clickhouse-client -u <OTEL_CLICKHOUSE_USER> --password <OTEL_CLICKHOUSE_PASSWORD> --query "SELECT 1"`
   - pulse-server: `curl http://localhost:<port>/healthcheck` (default 8080)
   - pulse-ui: `curl http://localhost:<port>/healthcheck.txt` (default 3000)
   - pulse-alerts-cron: `curl http://localhost:<port>/healthcheck` (default 4000)
8. Report status of all services as a table
9. Pulse AI: included in deploy stack; set `GOOGLE_API_KEY` in `deploy/.env` for Gemini. Health: `curl -sf http://localhost:8000/health`. Standalone: `cd pulse_ai && ./setup.sh`
10. Remind user to set API keys in env files if any service failed
