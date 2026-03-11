Start the Pulse Docker services for local development.

1. Change to the `deploy/` directory
2. Check if `.env` exists, if not copy from `.env.example`
3. Run `./scripts/start.sh -d` to start all services in detached mode
4. Run `docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"` to see all running containers
5. Wait for health checks to pass on all services:
   - MySQL: `docker exec pulse-mysql mysqladmin ping -h localhost`
   - ClickHouse: `docker exec pulse-clickhouse clickhouse-client -u <user from .env> --password <password from .env> --query "SELECT 1"`
   - Kafka: verify via `docker ps` that kafka container is healthy
   - OTEL Collector: `curl http://localhost:<port>/` (default 13133 — use port from step 4)
   - pulse-server: `curl http://localhost:<port>/healthcheck` (default 8080 — use port from step 4)
   - pulse-ui: `curl http://localhost:<port>/healthcheck.txt` (default 3000 — use port from step 4)
   - pulse-alerts-cron: `curl http://localhost:<port>/healthcheck` (default 4000 — use port from step 4)
6. Report which services are healthy and which failed

**Note:** pulse-ai runs via its own Docker Compose — start it separately with `cd pulse_ai && ./setup.sh`. Health check: `curl http://localhost:8000`.
