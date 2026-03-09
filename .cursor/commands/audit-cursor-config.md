Audit `.cursor/` configuration files against the codebase source-of-truth files and report any discrepancies.

1. Read the following source-of-truth files:
   - `deploy/docker-compose.yml` — services, ports, health checks, dependencies
   - `backend/ingestion/clickhouse-otel-schema.sql` — table schemas, materialized columns
   - `deploy/.env.example` — environment variable names
   - `deploy/scripts/build.sh` — accepted build targets
   - `deploy/scripts/start.sh` — accepted start targets

2. Cross-reference against `.cursor/` files for discrepancies:
   - **Service lists**: Compare services in docker-compose.yml against services listed in `.cursor/agents/`, `.cursor/commands/`, `.cursor/rules/`, `.cursor/skills/`
   - **Port numbers**: Verify ports in docker-compose.yml match those in rules and commands
   - **ClickHouse schema**: Verify table names, column names, and materialized columns in `data-analyst.md` match `clickhouse-otel-schema.sql`
   - **Environment variables**: Check that credential references in commands/skills match what's defined in `.env.example`
   - **Script options**: Verify build.sh/start.sh options documented in rules/skills match actual script arguments
   - **Health endpoints**: Verify health check URLs match what's defined in docker-compose health checks
   - **Pipeline architecture**: Verify data flow diagrams across agents/rules are consistent with docker-compose service topology

3. Report findings as a table:
   | File | Issue | Current Value | Expected Value |
   
4. For each discrepancy, suggest the specific edit needed to fix it

5. Ask the user if they want to auto-apply the fixes
