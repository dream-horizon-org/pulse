# domains / tenants

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [tiers](tiers.md), [usagelimits](usagelimits.md), [auth](../core/auth.md)

## Purpose

Tenant CRUD (public) and tenant→tier assignment (internal).

## Source

- `resources/tenants/TenantsController.java` (`@Path("/v1/tenants")`)
- `resources/tenants/InternalTenantsController.java`
  (`@Path("/internal/v1/tenants")`)
- `resources/tenants/TenantMapper.java`
- `resources/tenants/models/`
- `service/tenant/TenantService.java`, `TenantAuditAction.java`,
  `models/`
- `service/TenantMemberService.java`
- `dao/tenant/TenantDao.java`, `TenantQueries.java`, `models/`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/tenants` |
| POST | `/v1/tenants` |
| GET | `/v1/tenants/{tenantId}` |
| PUT | `/v1/tenants/{tenantId}` |
| PUT | `/v1/tenants/{tenantId}/deactivate` |
| PUT | `/v1/tenants/{tenantId}/activate` |
| PUT | `/internal/v1/tenants/{tenantId}/tier` |

## Internal design

- `TenantService` orchestrates CRUD + audit (`TenantAuditAction`).
- `TenantMemberService` manages user→tenant membership (consumed by auth).
- Internal tier-assignment endpoint is service-to-service.

## Dependencies

MySQL `tenants`, `tenant_members`; [tiers](tiers.md) (tier ids);
[auth](../core/auth.md) (membership checks).

## Data contracts

MySQL: `tenants(id, name, status, tier_id, created_at, ...)`,
`tenant_members(tenant_id, user_id, role)`.

## Tests

`src/test/java/.../resources/tenants/*`, `.../service/tenant/*`,
`TenantMemberServiceTest.java`.

## Rebuild recipe

1. Two controllers (public + internal).
2. `TenantService` with audit log of `TenantAuditAction` events.
3. `TenantDao` + `TenantQueries`.
4. Wire `TenantMemberService` into auth chain.
