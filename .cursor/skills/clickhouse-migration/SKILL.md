---
name: clickhouse-migration
description: Workflow for ClickHouse schema changes — adding columns, tables, or modifying the OTEL analytics schema. Use when making changes to ClickHouse tables in the otel database.
disable-model-invocation: true
---

# ClickHouse Migration

## Workflow

```
- [ ] Step 1: Update the schema file
- [ ] Step 2: Create migration script
- [ ] Step 3: Update init script
- [ ] Step 4: Update affected queries in backend
- [ ] Step 5: Update AI agent schema registry
- [ ] Step 6: Apply and verify
```

## Step 1: Update Schema File

Edit `backend/ingestion/clickhouse-otel-schema.sql` to reflect the final desired state. This file is used for fresh installs.

## Step 2: Create Migration Script

Create `deploy/db/migration-clickhouse-<description>.sql`:

```sql
-- Adding a new column
ALTER TABLE otel.otel_traces ADD COLUMN IF NOT EXISTS new_column String DEFAULT '';

-- Adding a new table
CREATE TABLE IF NOT EXISTS otel.my_new_table (
    Timestamp DateTime64(9) CODEC(Delta, ZSTD(1)),
    TraceId String CODEC(ZSTD(1)),
    -- ... columns ...
) ENGINE = MergeTree()
PARTITION BY toDate(Timestamp)
ORDER BY (TraceId, Timestamp)
SETTINGS index_granularity = 8192;
```

## Step 3: Update Init Script

If adding a new table, update `deploy/scripts/init-clickhouse.sh` to include it.

## Step 4: Update Backend Queries

Search for affected queries in:
- `backend/server/src/main/java/.../service/` — ClickhouseMetricService and related
- `backend/server/src/main/java/.../dao/` — any DAO querying the changed table

## Step 5: Update AI Agent

**Note:** The AI agent currently has a flat structure (`pulse_ai/agent.py`) with no registries. If the change affects queryable tables/columns, update the root agent's instruction in `pulse_ai/agent.py` to reflect the new schema, or update registry files at the `pulse_ai/` root when they are added.

## Step 6: Apply and Verify

```bash
# Apply to running ClickHouse
docker exec -i pulse-clickhouse clickhouse-client < deploy/db/migration-clickhouse-<desc>.sql

# Verify
docker exec pulse-clickhouse clickhouse-client --query "DESCRIBE otel.otel_traces"
```
