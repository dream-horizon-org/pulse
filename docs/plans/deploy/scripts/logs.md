# Script · `logs.sh`

Tails container logs across services.

Brief: [../../../components/deploy.md](../../../components/deploy.md) · Peers: [start-stop](./start-stop.md).

## Source location

- `deploy/scripts/logs.sh`

## Usage

```bash
./logs.sh [--no-follow] [--tail N] [service]
```

`service` ∈ `ui | server | cron | ai | mysql | clickhouse | otel | vector | kafka | session-capture | ...` (see the script's arg parser for the authoritative list).

## Flow

1. Source `common.sh`.
2. Parse flags:
   - `--no-follow` → drop the `-f` flag.
   - `--tail N` → `--tail=N` (validates numeric).
   - positional arg → service name (maps UI aliases to compose service ids).
3. If no service provided → tails all services in the project.
4. Prefer `docker compose logs`; fall back to `docker logs <container>` loop in CLI mode.

## Rebuild recipe

1. Source the common helpers.
2. Parse args; validate `--tail` is numeric.
3. Alias short service names (ui, server, ai, cron, otel) to compose service ids.
4. Default to `-f` so live-tailing is the easy path.
