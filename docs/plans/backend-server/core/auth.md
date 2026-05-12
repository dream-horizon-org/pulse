# core / auth

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

Authn (Google OAuth → JWT, dev-mode mock) and authz (OpenFGA + tenant/project
membership).

## Source

- `.../service/AuthService.java` (~906 lines)
- `.../service/JwtService.java` (~203 lines)
- `.../service/auth/LoginHostContext.java`
- `.../service/OpenFgaService.java`, `.../guice/OpenFgaServiceProvider.java`
- `.../service/ProjectMemberService.java`, `TenantMemberService.java`,
  `UserProjectsService.java`, `UserService.java`
- `.../service/devmode/*` (mock users)
- `.../verticle/VertxAuthChain.java`
- API-key auth: `.../service/apikey/ProjectApiKeyService.java`,
  `.../service/userapikey/UserApiKeyService.java`,
  `.../service/kong/KongApiKeyRedisSyncService.java`

## Public surface

| Concern | Entry |
|---|---|
| Login / refresh | `AuthService` (called by REST under `resources/internal` or v1 auth controllers) |
| JWT mint/parse | `JwtService` (access 24h, refresh 30d per CLAUDE.md) |
| API key validate | `/internal/v1/api-keys/valid` (apikeys domain) |
| Authz check | `OpenFgaService.check(user, relation, object)` |

## Internal design

- Production: Google OAuth 2.0 → mints JWT pair via `JwtService`.
- Dev: `GOOGLE_OAUTH_ENABLED=false` → `devmode` returns mock users
  `mock-user-1`, `mock-user-2`, project `default-project`, key
  `default-project_devkey01`.
- `VertxAuthChain` inspects `Authorization: Bearer <jwt>` and
  `x-api-key`; resolves user/project context and stashes into request.
- `OpenFgaService` (gRPC) backs role checks; `OpenFgaServiceProvider` is the
  Guice provider.
- Kong sync (`KongApiKeyRedisSyncService`) pushes valid keys to Redis so the
  gateway can short-circuit.

## Dependencies

MySQL `users`, `projects`, `project_members`, `tenants`, `tenant_members`,
`api_keys`, `user_api_keys`. OpenFGA. Google OAuth (`google-api-client`).

## Data contracts

- JWT claims: `sub`, `email`, `tenantId`, project list, `exp`, `iat`.
- API key format (dev): `<projectId>_<key>`.

## Tests

- `src/test/java/.../service/AuthServiceTest.java`
- `.../service/JwtServiceTest.java`
- `.../service/OpenFgaServiceTest.java`
- `.../service/ProjectMemberServiceTest.java`
- `.../service/TenantMemberServiceTest.java`
- `.../service/UserServiceTest.java`
- `.../service/UserProjectsServiceTest.java`
- `.../service/kong/*`

## Rebuild recipe

1. Add `JwtService` (HS256/RS256) with `mint(user)` and `verify(token)`.
2. `AuthService` handling OAuth callback + refresh.
3. `VertxAuthChain` Vert.x handler — reject 401 if neither JWT nor API key
   resolves.
4. Bind `OpenFgaService` via `OpenFgaServiceProvider`.
5. Dev-mode bypass behind `GOOGLE_OAUTH_ENABLED=false`.
