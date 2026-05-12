# domains / apikeys

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [auth](../core/auth.md), [tenants](tenants.md)

## Purpose

Project-scoped API keys + internal validation/sync used by Kong gateway.

## Source

- `resources/apikeys/ProjectApiKeysController.java`
  (`@Path("/v1/projects/{projectId}/api-keys")`)
- `resources/apikeys/InternalApiKeysController.java`
  (`@Path("/internal/v1/api-keys")`)
- `resources/apikeys/ApiKeyMapper.java`
- `resources/apikeys/models/`
- `service/apikey/ProjectApiKeyService.java`
- `service/apikey/models/`
- `service/kong/KongApiKeyRedisSyncService.java`
- `dao/apikey/ProjectApiKeyDao.java`, `ProjectApiKeyQueries.java`

## Public surface

| Method | Path | Class |
|---|---|---|
| GET | `/v1/projects/{projectId}/api-keys` | `ProjectApiKeysController` |
| POST | `/v1/projects/{projectId}/api-keys` | `ProjectApiKeysController` |
| DELETE | `/v1/projects/{projectId}/api-keys/{apiKeyId}` | `ProjectApiKeysController` |
| GET | `/internal/v1/api-keys/valid` | `InternalApiKeysController` |
| POST | `/internal/v1/api-keys/sync-to-redis` | `InternalApiKeysController` |

## Internal design

- Controller → `ProjectApiKeyService` → `ProjectApiKeyDao`.
- Keys persisted in MySQL; Kong consumes via Redis sync
  (`KongApiKeyRedisSyncService`).
- Dev-mode default key: `default-project_devkey01`.

## Dependencies

MySQL `api_keys` table; Redis (Kong gateway); [auth](../core/auth.md) for
authentication of management endpoints.

## Data contracts

`api_keys(id, project_id, name, hash, created_at, ...)`. Validation
returns project/tenant context.

## Tests

`src/test/java/.../resources/apikeys/*`, `.../service/apikey/*`,
`.../service/kong/*`.

## Rebuild recipe

1. `ProjectApiKeysController` + `InternalApiKeysController`.
2. Service signs/hashes secrets; never returns raw key after creation.
3. Sync-to-redis endpoint iterates active keys → SET in Redis with project
   metadata for Kong plugin.
