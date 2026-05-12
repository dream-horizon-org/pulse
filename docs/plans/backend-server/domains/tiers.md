# domains / tiers

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [tenants](tenants.md), [usagelimits](usagelimits.md)

## Purpose

Tier (plan) definitions: public read, internal write.

## Source

- `resources/tiers/TiersController.java` (`@Path("/v1/tiers")`)
- `resources/tiers/InternalTiersController.java`
  (`@Path("/internal/v1/tiers")`)
- `resources/tiers/TierMapper.java`
- `resources/tiers/models/`
- `service/tier/TierService.java`, `models/`
- `dao/tier/TierDao.java`, `TierQueries.java`, `models/`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/tiers` |
| GET | `/v1/tiers/{tierId}` |
| GET | `/internal/v1/tiers` |
| GET | `/internal/v1/tiers/{tierId}` |
| POST | `/internal/v1/tiers` |
| PUT | `/internal/v1/tiers/{tierId}` |
| PUT | `/internal/v1/tiers/{tierId}/deactivate` |
| PUT | `/internal/v1/tiers/{tierId}/activate` |

## Internal design

- Tier rows define quotas/feature flags consumed by [usagelimits](usagelimits.md)
  and [tenants](tenants.md).
- Mutations only via `/internal/*` controller (service-to-service).

## Dependencies

MySQL `tiers`. Consumed by `usagelimits` and `tenants`.

## Data contracts

MySQL: `tiers(id, name, features_json, quotas_json, status)`.

## Tests

`src/test/java/.../resources/tiers/*`, `.../service/tier/*`.

## Rebuild recipe

1. Public read-only controller; internal CRUD/activate-deactivate.
2. `TierService` + `TierDao` + `TierQueries`.
3. Reference from tenant + usage-limit services.
