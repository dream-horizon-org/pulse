Audit `.cursor/` configuration files against the codebase source-of-truth files and report any discrepancies.

1. Read the following source-of-truth files:
    - `deploy/docker-compose.yml` — services, ports, health checks, dependencies
    - `backend/db/prod/clickhouse/*.sql` — table schemas, materialized columns, heatmap aggregates (
      `interaction_heatmaps_daily`)
    - `backend/db/dev/clickhouse/funnel-results.sql` — single-node `funnel_results` (MergeTree)
    - `backend/db/dev/clickhouse/journey-results.sql` — single-node `journey_results` (MergeTree)
    - `backend/db/prod/clickhouse/funnel-results.sql` — cluster `funnel_results_local` /
      `journey_results_local` + Distributed tables
    - `backend/db/prod/clickhouse/otel.session_summary.sql` — session summary MV (referenced by init)
    - `deploy/.env.example` — environment variable names
    - `deploy/scripts/build.sh` — accepted build targets
    - `deploy/scripts/start.sh` — accepted start targets
    - `deploy/scripts/common.sh` — default exports for compose/script-driven env (including pulse-server `CONFIG_*`,
      EMR, Spark job vars, and `ANALYTICS_COMPUTE_ENGINE` / `ANALYTICS_BATCH_PROJECT_CONCURRENCY`)
    - Optional second stack: `deploy/observability-otel-prometheus-tempo/docker-compose.yml` — when auditing JVM/backend OTLP export, verify `obs_otel_collector` naming, published OTLP ports (14317/14318), and conflicts with Vector profile documented in `.cursor/rules/docker-deploy.mdc`


2. Cross-reference against `.cursor/` files for discrepancies:
    - **Service lists**: Compare services in docker-compose.yml against services listed in `.cursor/agents/`,
      `.cursor/commands/`, `.cursor/rules/`, `.cursor/skills/`
    - **Port numbers**: Verify ports in docker-compose.yml match those in rules and commands
    - **ClickHouse schema**: Verify table names, column names, and materialized columns in `data-analyst.md` match
      `backend/db/prod/clickhouse/*.sql`
    - **Environment variables**: Check that credential references in commands/skills match what's defined in
      `.env.example`
    - **Script options**: Verify build.sh/start.sh options documented in rules/skills match actual script arguments
    - **Health endpoints**: Verify health check URLs match what's defined in docker-compose health checks
    - **Pipeline architecture**: Verify data flow diagrams across agents/rules are consistent with docker-compose
      service topology

3. Report findings as a table:
   | File | Issue | Current Value | Expected Value |

4. For each discrepancy, suggest the specific edit needed to fix it

5. Ask the user if they want to auto-apply the fixes
