Check the health of all Pulse services and infrastructure.

1. First, run `docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"` to discover running containers and their
   ports
2. Read credentials from `deploy/.env` for services that require auth

Run health checks on all Docker services and report status:

1. **MySQL**: `docker exec pulse-mysql mysqladmin ping -h localhost` (uses Docker container's internal auth)
2. **ClickHouse**:
   `docker exec pulse-clickhouse clickhouse-client -u <OTEL_CLICKHOUSE_USER from .env> --password <OTEL_CLICKHOUSE_PASSWORD from .env> --query "SELECT 1"`
3. **OpenFGA**: `curl http://localhost:8180/healthz` (verify port from `docker ps` for pulse-openfga)
4. **OTEL Collector**: `curl http://localhost:<port>/` (default 13133 — verify from `docker ps`)
5. **pulse-server**: `curl http://localhost:<port>/healthcheck` (default 8080 — verify from `docker ps`)
6. **pulse-ui**: `curl http://localhost:<port>/healthcheck.txt` (default 3000 — verify from `docker ps`)
7. **pulse-alerts-cron**: `curl http://localhost:<port>/healthcheck` (default 4000 — verify from `docker ps`)
8. **pulse-session-capture**: `curl http://localhost:3400/healthcheck` (verify port from `docker ps` if remapped)
9. **Kafka**: `docker exec pulse-kafka kafka-topics --bootstrap-server localhost:9092 --list` (smoke check)
10. **MinIO**: open console at `http://localhost:9101` or `curl` S3 API on host port from `docker ps` (default 9100)
8. **pulse-ai-agent**: `curl -sf http://localhost:8000/health` (default deploy stack; standalone:
   `cd pulse_ai && ./setup.sh`)
9. **Kafka** (if running): `docker exec pulse-kafka kafka-topics --bootstrap-server localhost:9092 --list` (default
   broker port 9092)
10. **MinIO** (if running): verify from `docker ps` (host API often 9100, console 9101)

Present results as a table with service name, status (healthy/unhealthy/not running), and port.
