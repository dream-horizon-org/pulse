# Script · `quickstart.sh`

One-shot: build + start everything.

Brief: [../../../components/deploy.md](../../../components/deploy.md) · Peers: [start-stop](./start-stop.md), [logs](./logs.md).

## Source location

- `deploy/scripts/quickstart.sh`
- Shared helpers: `deploy/scripts/common.sh`

## Usage

```bash
cd deploy && ./scripts/quickstart.sh [--no-cache] [--skip-env-check]
```

## Flow

1. Source `common.sh` — provides colors, `print_*`, `check_docker`, `has_compose`, `run_compose`, `load_env`, constants.
2. `_quickstart_has_google_api_key` scans `deploy/.env` for a non-commented `GOOGLE_API_KEY` — if missing, `pulse-ai-agent` is skipped (since Gemini won't authenticate).
3. `validate-env-variables.sh` unless `--skip-env-check`.
4. `check_docker` + `ensure_compose`.
5. `build.sh` (or `docker compose build`) with `--no-cache` if requested.
6. `start.sh -d` — detached start with dependency-ordered healthcheck gating.
7. Prints where each service is reachable (UI on :3000, API on :8080, etc.).

## Notes

- Falls back to raw Docker CLI when Compose isn't available, but OpenFGA + `pulse-ai-agent` require Compose; CLI-fallback skips them with a banner.
- Auto-detects `docker compose` (v2 plugin) vs `docker-compose` (v1 binary).

## Rebuild recipe

1. Put shared helpers in `common.sh` (idempotent, sourced by every script).
2. Split build from start so CI can reuse build artifacts.
3. Gate optional services on presence of their secrets.
4. Exit non-zero on validation or docker check failure.
