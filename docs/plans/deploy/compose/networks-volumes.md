# Compose · Networks & Volumes

Brief: [../../../components/deploy.md](../../../components/deploy.md) · Peers: [services](./services.md), [env-vars](./env-vars.md).

## Networks

Single bridge network declared at the bottom of `deploy/docker-compose.yml`:

```yaml
networks:
  pulse-network:
    driver: bridge
```

Every service attaches to it so DNS resolves by service name (e.g. `mysql`, `clickhouse`, `otel-collector`, `kafka`).

## Named volumes

Typical set (exact names per YAML):

| Volume | Mounted by | Purpose |
|---|---|---|
| `mysql-data` | `mysql` | Persists MySQL across restarts |
| `clickhouse-data` | `clickhouse` | Persists ClickHouse telemetry DB |
| `kafka-data` / `zookeeper-data` | Kafka, ZK | Replay/heatmap bus durability |
| `vector-data` | `vector` | Vector's disk buffer (`/var/lib/vector`) |
| `openfga-data` | `openfga` | OpenFGA state |
| `redis-data` | Redis (heatmap dedup) | Screenshot-hash cache |

Declared under a top-level `volumes:` block.

## Bind mounts

- `mysql-init.sql`: bind-mount from `../backend/db/dev/mysql/mysql-init.sql` into `/docker-entrypoint-initdb.d/`.
- `./openfga` for OpenFGA init scripts (read-only).
- ClickHouse init comes through `scripts/init-clickhouse.sh` hitting the running container (not a mount).

## Operational notes

- `./scripts/stop.sh -v` removes the named volumes (resets state).
- `./scripts/reset-databases.sh` is destructive — gated behind explicit confirmation per CLAUDE.md safety rules.

## Rebuild recipe

1. Declare `pulse-network: { driver: bridge }`.
2. Add a named volume for every stateful service.
3. Bind-mount init SQL/scripts read-only.
4. Never put data on anonymous volumes — migrations would silently reset.
