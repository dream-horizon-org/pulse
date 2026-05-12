# Scripts · `start.sh` / `stop.sh`

Brief: [../../../components/deploy.md](../../../components/deploy.md) · Peers: [quickstart](./quickstart.md), [logs](./logs.md).

## `start.sh`

- Source: `deploy/scripts/start.sh`.
- Usage: `./start.sh [-d|--detach] [--build] [--no-cache] [--skip-env-check]`.
- Flow:
  1. Source `common.sh` for helpers.
  2. `check_docker` + `ensure_compose` + `load_env`.
  3. Parse args for `-d` (detached) and `--build`.
  4. Invoke `run_compose up -d` (or dependency-ordered Docker CLI fallback with healthcheck polls).
  5. Print service endpoints on success.

Uses Compose when available. CLI fallback sequences startups by depends_on/healthcheck — OpenFGA + `pulse-ai-agent` are skipped in CLI-mode per the script banner.

## `stop.sh`

- Source: `deploy/scripts/stop.sh`.
- Usage: `./stop.sh [-v]`. `-v` removes volumes (destructive).
- Flow:
  1. Source `common.sh`.
  2. `docker compose down` (or CLI `docker stop` loop).
  3. If `-v`: also `docker compose down -v` / `docker volume rm ...`.

## Safety

- `-v` is destructive; use with intent.
- `reset-databases.sh` (sibling) is a separate escape hatch that wipes data without removing volumes — requires explicit confirmation per CLAUDE.md.

## Rebuild recipe

1. Build on top of `common.sh`.
2. Keep the Compose path primary, CLI path secondary but functional for the essentials.
3. Print endpoint table at end of `start.sh` for new contributors.
4. Keep `stop.sh` symmetric with `start.sh`; make `-v` loud (prompt or banner).
