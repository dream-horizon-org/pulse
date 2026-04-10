View logs for a specific Pulse service.

1. Ask which service to view logs for if not specified. Available Docker services: pulse-server, pulse-ui, pulse-alerts-cron, pulse-ai-agent, otel-collector, mysql, clickhouse, openfga (vector optional: enable via VECTOR_ENABLED=true)
2. Run `docker logs --tail 100 <container-name>` (container names are prefixed with `pulse-`, e.g., `pulse-mysql`, `pulse-clickhouse`, `pulse-otel-collector`, `pulse-openfga`)
3. Alternatively, use `cd deploy && ./scripts/logs.sh <service-name>` with docker-compose service names (e.g., `pulse-server`, `mysql`, `otel-collector`)
4. Scan for errors or warnings and highlight them
5. If errors found, suggest potential causes and fixes

**Note:** Integrated AI: `cd deploy && ./scripts/logs.sh ai`. Standalone: `cd pulse_ai && ./setup.sh logs`.
