---
name: clickhouse-migration
description: Workflow for ClickHouse schema changes — adding columns, tables, or modifying the OTEL analytics schema. Use when making changes to ClickHouse tables in the otel database.
disable-model-invocation: true
---

# ClickHouse Migration

## Layout

| Environment | Liquibase changelog |
|-------------|---------------------|
| **Local / Docker** | `backend/db/migrations/clickhouse/dev/` — single-node `MergeTree` DDL |
| **Production cluster** | `backend/db/migrations/clickhouse/prod/` — `Replicated*` + `Distributed`, `ON CLUSTER 'pulse-ch'` |

Edit SQL directly under the matching folder. Do not use a separate DDL tree outside `migrations/`.

## Workflow

```
- [ ] Step 1: Add changesets in dev/ and/or prod/ (V0002+ for incremental)
- [ ] Step 2: Register new files in changelog-root.xml
- [ ] Step 3: Update affected queries in backend
- [ ] Step 4: Update AI agent schema registry
- [ ] Step 5: Apply and verify
```

## Step 1: Changesets

**Incremental** (`V0002+`) — add a new file under `backend/db/migrations/clickhouse/dev/` and/or `backend/db/migrations/clickhouse/prod/` using Liquibase formatted SQL :

```sql
--liquibase formatted sql

--changeset db-migrations:V0002__add_my_column runOnChange:false failOnError:true splitStatements:true endDelimiter:; dbms:clickhouse
--comment Short description

ALTER TABLE otel.otel_traces ADD COLUMN IF NOT EXISTS new_column String DEFAULT '';
```

**Prod cluster** (example):

```sql
ALTER TABLE otel.otel_traces_local ON CLUSTER 'pulse-ch' ADD COLUMN IF NOT EXISTS new_column String DEFAULT '';
```

Never edit applied `V000*` on databases that already ran them; add a new changeset instead.

## Step 2: Register in Changelog

Add an include entry like `<include file="V0002__....sql" relativeToChangelogFile="true"/>` to the matching `changelog-root.xml` (never edit existing includes).

## Step 3: Update Backend queries

- `backend/server/src/main/java/.../service/` — ClickhouseMetricService and related
- `backend/server/src/main/java/.../dao/` — any DAO querying the changed table

## Step 4: Update AI Agent

**Note:** The AI agent currently has a flat structure (`pulse_ai/agent.py`) with no registries. If the change affects
queryable tables/columns, update the root agent's instruction in `pulse_ai/agent.py` to reflect the new schema, or
update registry files at the `pulse_ai/` root when they are added.
When you add or rename tables, materialized columns, or 
row-policy targets, update `.cursor/` docs to match (at minimum
`agents/data-analyst.md`, `rules/clickhouse-sql.mdc`, `rules/
pulse-architecture.mdc`, `commands/query-clickhouse.md`) or
run `/audit-cursor-config` to catch drift.

## Step 5: Apply

**Local (Docker):** `pulse-db-migrate` uses `migrations/clickhouse/dev/changelog-root.xml`.

```bash
cd deploy && ./scripts/start.sh -d
```

**Manual Liquibase (dev changelog):**

```bash
cd backend/db
mvn -B liquibase:update -Pclickhouse \
  -Dliquibase.clickhouse.changeLogFile=migrations/clickhouse/dev/changelog-root.xml \
  -Dliquibase.clickhouse.url="jdbc:clickhouse://localhost:8123/otel" \
  -Dliquibase.clickhouse.username="${OTEL_CLICKHOUSE_USER:-pulse_user}" \
  -Dliquibase.clickhouse.password="${OTEL_CLICKHOUSE_PASSWORD:-pulse_password}"
```

**Production:** Jenkins uses `migrations/clickhouse/prod/changelog-root.xml` (`db-migrations-sync`, `pulse-server-prod-deployment`). `db-migrations-sync` is run only for the first time to synchronize the changelog in production databases.

```bash
docker exec pulse-clickhouse clickhouse-client --query "DESCRIBE otel.otel_traces"
```
