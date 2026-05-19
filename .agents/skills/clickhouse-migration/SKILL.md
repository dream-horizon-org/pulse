---
name: clickhouse-migration
description: Workflow for ClickHouse schema changes — adding columns, tables, or modifying the OTEL analytics schema. Use when making changes to ClickHouse tables in the otel database.
disable-model-invocation: true
---

# ClickHouse Migration

## Workflow

```
- [ ] Step 1: Add Liquibase migration under backend/db/migrations/clickhouse/
- [ ] Step 2: Register changeset in changelog-root.xml
- [ ] Step 3: Update affected queries in backend
- [ ] Step 4: Update AI agent schema registry
- [ ] Step 5: Apply and verify
```

## Step 1: Add Liquibase Migration

Add a new file under `backend/db/migrations/clickhouse/` (e.g. `V0002__add_my_column.sql`) using Liquibase formatted SQL:

```sql
--liquibase formatted sql

--changeset db-migrations:V0002__add_my_column runOnChange:false failOnError:true
--comment Short description of the change

ALTER TABLE otel.otel_traces ADD COLUMN IF NOT EXISTS new_column String DEFAULT '';
```

## Step 2: Register in Changelog

Add an `<include>` entry to `backend/db/migrations/clickhouse/changelog-root.xml` (never edit existing includes).

When you add or rename tables, materialized columns, or row-policy targets, update `.cursor/` docs to match (at minimum
`agents/data-analyst.md`, `rules/clickhouse-sql.mdc`, `rules/pulse-architecture.mdc`, `commands/query-clickhouse.md`) or
run `/audit-cursor-config` to catch drift.

## Step 3: Update Backend Queries

Search for affected queries in:

- `backend/server/src/main/java/.../service/` — ClickhouseMetricService and related
- `backend/server/src/main/java/.../dao/` — any DAO querying the changed table

## Step 4: Update AI Agent

**Note:** The AI agent currently has a flat structure (`pulse_ai/agent.py`) with no registries. If the change affects
queryable tables/columns, update the root agent's instruction in `pulse_ai/agent.py` to reflect the new schema, or
update registry files at the `pulse_ai/` root when they are added.

## Step 5: Apply and Verify

**Local (Docker Compose):** `pulse-db-migrate` runs automatically after MySQL and ClickHouse are healthy.

```bash
cd deploy && ./scripts/start.sh -d
# Or re-apply on existing DBs:
docker logs pulse-db-migrate
```

**Manual Liquibase (same as the migrate container):**

```bash
cd backend/db
mvn -B liquibase:update -Pclickhouse \
  -Dliquibase.clickhouse.url="jdbc:clickhouse://localhost:8123/otel" \
  -Dliquibase.clickhouse.username="${OTEL_CLICKHOUSE_USER:-pulse_user}" \
  -Dliquibase.clickhouse.password="${OTEL_CLICKHOUSE_PASSWORD:-pulse_password}"
```

**Production:** Jenkins `db-migrations-sync` (status/validate/changelogSync) one time exercise and only `pulse-server-prod-deployment` (`liquibase:update` before Terraform apply) after the first time.

```bash
docker exec pulse-clickhouse clickhouse-client --query "DESCRIBE otel.otel_traces"
```
