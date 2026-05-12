# MySQL — migrations / bootstrap workflow

## Purpose

Document the (deliberately minimal) MySQL schema-evolution process — there is no Flyway/Liquibase, only init SQL.

## Source

- `backend/db/dev/mysql/mysql-init.sql`
- `backend/db/prod/mysql/mysql-init.sql`
- `deploy/db/mysql-init.sql` (copy used by Compose mount).

The three files are kept in sync by convention.

## Mechanics

- Docker MySQL image runs every `.sql` under `/docker-entrypoint-initdb.d/` exactly once, against an **empty data dir**. The Compose mount points there.
- If `mysql-data` volume already has data, edits to the SQL are ignored.

## Workflows

### Dev: full reset
```
./deploy/scripts/reset-databases.sh   # confirms with "yes"
```
Drops `pulse-mysql-data` + `pulse-clickhouse-data` and restarts → MySQL re-runs `mysql-init.sql`, ClickHouse re-applies `backend/db/dev/clickhouse/*.sql` via `init-clickhouse.sh`.

### Dev: incremental edit on running stack
Apply the diff by hand inside the container:
```
docker exec -i pulse-mysql mysql -upulse_user -p"$MYSQL_PASSWORD" pulse_db < my-diff.sql
```
Then commit the same change into `mysql-init.sql` so the next fresh boot includes it.

### Prod
Edit `backend/db/prod/mysql/mysql-init.sql`, apply diff to the running cluster through the standard DBA channel, then bake into image / Ansible.

## Dev vs prod differences

- Dev: seeded tenants (`default`, `Fancode`, `Dream11`), mock users, default-project interactions, sample SDK config row, dev API key `default-project_devkey01`.
- Prod: no seed; all rows created via API.

## Targeted fix scripts

- `deploy/scripts/create-users-table.sh` — emits just the `users` DDL, for the common bootstrap-order failure ("Table `pulse_db.users` doesn't exist").
- `deploy/scripts/sync-default-tenant-ch-credentials.py` — re-syncs `clickhouse_project_credentials` password for the default tenant against `.env`.

## Failure modes

- Diverging `dev/` vs `prod/` vs `deploy/db/` copies → bugs that only manifest in one environment. Always edit all three.
- Forgetting that the init SQL is bootstrap-only — "I changed the file, why isn't it picking up?" → volume already initialised.

## Related

- `clickhouse/migrations.md` — parallel story for ClickHouse.
- `deploy/scripts/reset-databases.sh`, `init-clickhouse.sh`.

## Open questions

- Whether to introduce Flyway / Liquibase. No decision recorded.
