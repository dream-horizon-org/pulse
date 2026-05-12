# ClickHouse — migrations / bootstrap

## Purpose

Document how the `otel.*` schema is created and evolved.

## Source

- `backend/db/dev/clickhouse/01_…16_otel.<name>.sql` — applied in lex order (numeric prefix). Includes companion `CREATE MATERIALIZED VIEW` statements where applicable.
- `backend/db/prod/clickhouse/otel.<name>.sql` — same tables, flat names.

## Apply mechanism

Dev / Compose: `deploy/scripts/init-clickhouse.sh` runs as a one-shot inside a ClickHouse client container:

1. Waits for ClickHouse to accept connections.
2. Applies every `.sql` in `backend/db/dev/clickhouse/` against the admin user.
3. Exits (Compose service `clickhouse-init` is `restart: "no"`).

Production: equivalent SQL is applied by ops tooling (Ansible / Terraform in `deploy/`), and via the per-table prod files. There is no embedded migration tool.

## Important constraint

`backend/ingestion/otel-collector.yaml` sets `exporters.clickhouse.create_schema: false`. The Collector therefore expects the tables (`otel_traces`, `otel_logs`, `otel_metrics_*`) to exist before traffic. If you start the Collector before `clickhouse-init` finishes, the first batches fail with "table does not exist" and the retry loop kicks in. The `start.sh` dependency order avoids this.

## File numbering (dev)

The numeric prefix is the apply order, not a version number. New tables append at the next number; deletions leave a gap (do not renumber existing files — diffing across branches gets painful).

## Adding a table

1. Author `NN_otel.<name>.sql` in `backend/db/dev/clickhouse/` with the full DDL + any MVs.
2. Author the prod twin at `backend/db/prod/clickhouse/otel.<name>.sql`.
3. If tenant-readable, extend `ClickhouseProjectService` to issue row policies (see `row-policies.md`).
4. Reset dev (`reset-databases.sh`) or apply by hand on running clusters.

## Adding a materialized column on existing table

```sql
ALTER TABLE otel.<tbl>
  ADD COLUMN <Name> <Type> MATERIALIZED <expr>;
-- optional backfill:
ALTER TABLE otel.<tbl> MATERIALIZE COLUMN <Name>;
```

Bake the change into the table's `.sql` so fresh deploys are correct.

## Failure modes

- New file lands without prod twin → prod deploys lack the table.
- Dropping a column referenced by a MV → MV insert fails.
- Editing a file post-bootstrap and expecting auto-apply — `init-clickhouse.sh` only runs once per fresh boot.

## Related

- `otel-tables.md`, `materialized-columns.md`, `row-policies.md`.
- `/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/deploy/scripts/quickstart.md`.

## Open questions

- Long-term: introduce a real migration tool (e.g. `clickhouse-migrate` / `golang-migrate`) so dev/prod files don't diverge silently.
