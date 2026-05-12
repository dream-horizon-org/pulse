# MySQL — tenants, projects, API keys, usage

## Purpose

The multi-tenant root. Every other domain object is scoped under a `tenant_id` (and usually a `project_id`). Tiers + usage limits drive plan enforcement; API keys authenticate SDK traffic; ClickHouse credentials per project enforce data isolation.

## Source

`backend/db/{dev,prod}/mysql/mysql-init.sql` — sections "TIERS", "TENANTS TABLE", "Users and Projects tables", "DEV MODE: Mock Users", and the credentials / usage tables further down.

## Tables

- `tiers` — defines tier names and default usage limits. Seeded with default tiers.
- `tenants` — top-level org boundary. Seeded with `default` tenant.
- `users` — authentication / membership rows; supports Google OAuth and dev mock users.
- `projects` — `(tenant_id, project_id)`; project_id is human-readable (e.g. `default-project`).
- `project_api_keys` — per-project SDK keys; default key in dev = `${DEV_MODE_API_KEY}` (e.g. `default-project_devkey01`).
- `project_usage_limits` — overrides on top of tier defaults; per-project.
- `clickhouse_project_credentials` — per-project ClickHouse user + (encrypted) password used by backend for tenant queries.
- `clickhouse_project_credential_audit` — change log for the above.
- `tnc_versions`, `tnc_acceptances` — Terms & Conditions per user.

## Inputs

Created via:
- `pulse-server` REST: onboarding flow → `tenants` / `projects` rows + `project_api_keys` + `clickhouse_project_credentials` (plus runtime CH user + row policies, see `clickhouse/row-policies.md`).
- Dev seed in init SQL.
- `ProjectService.createProject()` writes the default `pulse_sdk_configs` row from the inline template.

## Outputs

- API-key auth middleware in `pulse-server` looks up `project_api_keys`.
- `ClickhouseProjectService` provisions/rotates per-project CH users using `clickhouse_project_credentials`.
- Alert + analytics jobs scope queries by `project_id`.

## Operational notes

- Dev API key format: `<project_id>_<suffix>`. Keep the pattern for dev clients so emulators continue to hit local backends.
- Tenants `Fancode` and `Dream11` exist purely as multi-tenant examples in dev seed.

## Failure modes

- CH password drift between MySQL row and CH user → AI Root Cause fails with "Authentication failed". Resync with `deploy/scripts/sync-default-tenant-ch-credentials.py`.
- Missing API-key row → SDK 401.

## Related code

- `backend/server/.../service/ProjectService.java`, `ClickhouseProjectService.java`.
- OpenFGA relationships are seeded via `deploy/openfga/init-openfga.sh` (mock-user-1/2 + default tenant).

## Open questions

- Encryption-at-rest for `clickhouse_project_credentials.password` depends on backend `VAULT_SERVICE_*` configuration; mode is not enforced by the schema.
