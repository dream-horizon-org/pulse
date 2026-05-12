# Service-to-Service Authentication Plan for pulse-server

---

## 1. Current Flow

### User-facing requests

Every protected request goes through two JAX-RS filters in priority order:

1. **`TenantFilter`** (`@Priority(AUTHENTICATION + 10)`, file: `tenant/TenantFilter.java`)
   - Extracts `tenantId` from the JWT `tenantId` claim and writes it to `TenantContext`.
   - Extracts `X-Project-ID` header and writes it to `ProjectContext`.
   - Supports `X-API-KEY` header as an alternative credential.
   - For system-role tokens (`superadmin`/`internal_viewer`), allows `X-Tenant-ID` override.
   - **`INTERNAL_PATH_PREFIX` constant (`"internal/"`) exists in the file (line 48) but is NOT in `isExcludedPath()`.** `/internal/*` paths are processed by `TenantFilter` like any other path. The filter attempts `jwtService.verifyToken(token)` on the Bearer token; if that throws (e.g., the token is not a valid signed JWT) it catches silently at line 195, logs at DEBUG, and continues — leaving `TenantContext` empty. No abort occurs in `TenantFilter` for a bad or missing token.

2. **`AuthorizationFilter`** (`@Priority(AUTHORIZATION)`, file: `filter/AuthorizationFilter.java`)
   - `/internal/*` is **not** in the `isExcludedPath()` list (lines 132–147).
   - Reads `@RequiresPermission` from the resource method or class.
   - If the annotation value equals `Constants.RELATION_SUPERADMIN` (`"superadmin"`), it calls `openFgaService.isSuperAdmin(userId)` — no project context required.
   - If no annotation is present, **returns early at line 68 — no permission check at all.**
   - `extractUserIdFromToken()` calls `jwtService.verifyToken(token)` on the Bearer string. If the token is an opaque secret (not a signed JWT), parsing throws and the method returns `null`, causing `abortUnauthorized(401)` before any OpenFGA check.

### JWT structure

Signed with `CONFIG_SERVICE_APPLICATION_JWTSECRET` (HMAC-SHA256, ≥32 chars).

```
{
  "sub": "<user-uuid>",
  "email": "...",
  "name": "...",
  "type": "access" | "refresh",
  "tenantId": "<tenant-uuid>",
  "systemRole": "superadmin" | "internal_viewer",  // absent for regular users
  "iat": ...,
  "exp": ...
}
```

### Internal endpoints — actual current state

Controllers under `/internal/*` fall into two categories:

**Category A — annotated with `@RequiresPermission(Constants.PERMISSION_SUPERADMIN)` (`"superadmin"`):**

| Controller | File | Annotated endpoints |
|---|---|---|
| `InternalApiKeysController` | `resources/apikeys/InternalApiKeysController.java` | `GET /internal/v1/api-keys/valid` |
| `InternalTenantsController` | `resources/tenants/InternalTenantsController.java` | `PUT /internal/v1/tenants/{tenantId}/tier` |
| `InternalTiersController` | `resources/tiers/InternalTiersController.java` | `POST /internal/v1/tiers`, `PUT /internal/v1/tiers/{tierId}`, `PUT /internal/v1/tiers/{tierId}/deactivate`, `PUT /internal/v1/tiers/{tierId}/activate`, `GET /internal/v1/tiers`, `GET /internal/v1/tiers/{tierId}` |
| `InternalUsageLimitsController` | `resources/usagelimits/InternalUsageLimitsController.java` | `GET /internal/v1/projects/{projectId}/limits`, `GET /internal/v1/projects/limits`, `PUT /internal/v1/projects/{projectId}/limits`, `POST /internal/v1/projects/{projectId}/limits/reset`, `GET /internal/v1/projects/{projectId}/limits/history`, `POST /internal/v1/projects/{projectId}/limits/notifications` |

For Category A endpoints: `AuthorizationFilter` calls `openFgaService.isSuperAdmin(userId)`. The `userId` comes from the `sub` claim of a valid signed JWT. A caller must therefore provide a Bearer token that is a valid HS256 JWT signed with `CONFIG_SERVICE_APPLICATION_JWTSECRET`, containing a `sub` claim whose value maps to a user that OpenFGA recognises as having the `superadmin` relation on `system:pulse`.

**Category B — NO `@RequiresPermission` annotation:**

| Controller | File | Unannotated endpoints |
|---|---|---|
| `InternalAnalyticsController` | `resources/analytics/InternalAnalyticsController.java` | `POST /internal/analytics/funnels`, `POST /internal/analytics/journeys`, `POST /internal/analytics/events` |
| `InternalApiKeysController` | `resources/apikeys/InternalApiKeysController.java` | `POST /internal/v1/api-keys/sync-to-redis` |
| `InternalUsageLimitsController` | `resources/usagelimits/InternalUsageLimitsController.java` | `POST /internal/v1/projects/limits/sync-to-redis`, `POST /internal/v1/projects/limits/process-usage-notifications` |

For Category B endpoints: `AuthorizationFilter` exits at line 68 (no annotation → no check). Any HTTP request with or without a token reaches the controller. These are **still effectively open**.

### pulse-alerts-cron — current state

`PulseServerApiClient` (file: `backend/pulse-alerts-cron/src/main/java/org/dreamhorizon/pulsealertscron/client/PulseServerApiClient.java`) sends on every call:

```
Authorization: Bearer <serviceJwtSecret>
```

`serviceJwtSecret` is loaded from env var `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` via HOCON key `app.serviceJwtSecret` in `backend/pulse-alerts-cron/src/main/resources/conf/application-default.conf`.

The value is a **raw opaque secret string**, not a signed JWT.

- Against Category B endpoints (unannotated): the raw secret passes through — `AuthorizationFilter` skips those, `TenantFilter` swallows the parse error silently. Calls succeed today but by accident (no gate, not because the token is valid).
- Against Category A endpoints (superadmin-annotated): the raw secret causes `jwtService.verifyToken()` to throw, `extractUserIdFromToken()` returns `null`, and `AuthorizationFilter` aborts with 401. The cron **cannot** reach Category A endpoints today.

The cron does not call any Category A endpoints today, which is why this hasn't caused failures. All cron-called endpoints (`/funnels`, `/journeys`, `/events`, `/limits/sync-to-redis`, `/api-keys/sync-to-redis`, `/limits/process-usage-notifications`) are in Category B.

### pulse-ai — current state

`pulse_ai/tool_session_auth.py` forwards the end-user's own Bearer JWT from the ADK session state. The AI agent acts on behalf of the logged-in user and calls only regular user-facing endpoints. It does not call `/internal/*` endpoints and needs no service identity.

---

## 2. Problem Statement

The current state has two distinct problems:

**Problem 1 — Category B endpoints are completely open.**  
`/internal/analytics/funnels`, `/internal/analytics/journeys`, `/internal/analytics/events`, `/internal/v1/api-keys/sync-to-redis`, `/internal/v1/projects/limits/sync-to-redis`, and `/internal/v1/projects/limits/process-usage-notifications` have no `@RequiresPermission` annotation. Any process that can reach pulse-server's port can trigger batch jobs or Redis sync operations without any credential.

**Problem 2 — pulse-alerts-cron cannot authenticate against Category A endpoints.**  
The cron sends `Authorization: Bearer <raw-secret>` but pulse-server's filter chain now requires a valid signed JWT for any `@RequiresPermission(superadmin)` endpoint. If the cron ever needs to call a Category A endpoint (e.g., `/internal/v1/api-keys/valid` to sync API keys from MySQL), it will receive 401. The token the cron already sends is a raw opaque string and will never pass `jwtService.verifyToken()`.

**Root cause of both problems:**  
There is no dedicated filter that handles the `/internal/*` path boundary before the general-purpose `TenantFilter`/`AuthorizationFilter` pair. The current architecture has two mismatched assumptions:
- Some internal endpoints were hardened with `@RequiresPermission(superadmin)` but the callers (cron) cannot produce a superadmin JWT.
- Some internal endpoints were left unannotated, relying on the old (now-removed) `isExcludedPath("internal/")` exclusion in both filters — that exclusion no longer exists.

---

## 3. Token Design

### Why a static opaque token still works for the internal boundary

The internal endpoints do not carry user identity. They are infrastructure operations on the system as a whole. An opaque shared secret validated by a dedicated `InternalServiceAuthFilter` is the right boundary:

- Simpler than issuing a superadmin JWT: no user row needed in MySQL, no OpenFGA relation to maintain, no token expiry/rotation ceremony.
- The cron already sends `Authorization: Bearer <serviceJwtSecret>`. The env var and header format are in place — only server-side validation is missing.
- `isSuperAdmin(userId)` in OpenFGA is designed for human admin users navigating the UI; mapping a service account to a superadmin OpenFGA relation would pollute the human-readable admin roster.

### What changes from the old plan

The old plan assumed `AuthorizationFilter` and `TenantFilter` would pass through `/internal/*` paths unimpeded. That is no longer the case:

- A new `InternalServiceAuthFilter` must run at `@Priority(Priorities.AUTHENTICATION)` — before `TenantFilter` (`AUTHENTICATION + 10`) and `AuthorizationFilter` (`AUTHORIZATION`).
- When `InternalServiceAuthFilter` authenticates an internal service call, it must **mark the request as authenticated** so that `AuthorizationFilter` does not attempt to parse a service token as a user JWT for the `@RequiresPermission(superadmin)` endpoints (Category A).
- The cleanest mechanism: after token validation passes, `InternalServiceAuthFilter` removes (or replaces) the `Authorization` header so downstream filters do not attempt JWT parsing, OR it sets a request property that `AuthorizationFilter` checks before proceeding.
- Preferred approach: set a JAX-RS request property `pulse.internal.authenticated = true`. Modify `AuthorizationFilter` to check this property and return early if present. This avoids header mutation.
- `TenantFilter` already silently skips if JWT parsing fails and produces no tenant context — that is acceptable for internal calls which have no tenant scope.

### Token format

An opaque string, minimum 32 characters, stored in AWS Secrets Manager for production. The cron's existing env var `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` carries this value and is already sent as the Bearer token. No cron-side changes are needed.

### Service identity claim

Optional `X-Service-Name` header (e.g., `alerts-cron`) logged by `InternalServiceAuthFilter` for audit. No code change in callers is required; the filter reads it if present.

---

## 4. Storage and Distribution

### AWS Secrets Manager (production)

| Secret name | Service | Description |
|---|---|---|
| `pulse/service-token/alerts-cron` | pulse-alerts-cron | Token for all `/internal/*` calls |
| `pulse/service-token/pulse-ai` | pulse-ai | Reserved for any future direct internal calls |

pulse-server loads `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS` — a comma-separated list of valid tokens. AWS Secrets Manager injects this into the ECS task environment at deploy time.

### Environment variables (dev/local)

```bash
# pulse-server — list of valid service tokens
CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS=dev-service-token-alertscron

# pulse-alerts-cron — its own token (must appear in pulse-server's list)
CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET=dev-service-token-alertscron
```

### Dev mode

When `GOOGLE_OAUTH_ENABLED=false`, `InternalServiceAuthFilter` short-circuits to allowed, matching the bypass pattern in `AuthorizationFilter` for OpenFGA (`getOpenFgaService().isEnabled()`).

---

## 5. pulse-server Validation — Filter Chain Changes

### New class: `InternalServiceAuthFilter`

**File to create:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/InternalServiceAuthFilter.java`

**Priority:** `@Priority(Priorities.AUTHENTICATION)` — runs before `TenantFilter` (`AUTHENTICATION + 10`) and before `AuthorizationFilter` (`AUTHORIZATION`).

**Logic:**

```
InternalServiceAuthFilter.filter(requestContext):
  path = requestContext.getUriInfo().getPath()
  if NOT path (normalized) starts with "internal/":
      return  // not an internal path — do nothing

  authHeader = requestContext.getHeaderString(Authorization)
  if authHeader is null or not starts with "Bearer ":
      abortWith 401
      return

  token = authHeader after "Bearer "
  if token is blank:
      abortWith 401
      return

  if GOOGLE_OAUTH_ENABLED == false:
      // dev mode: allow all internal calls without token check
      requestContext.setProperty("pulse.internal.authenticated", true)
      return

  validTokens = applicationConfig.getInternalServiceTokenSet()  // Set<String>
  if validTokens is empty:
      log.warn("No internal service tokens configured — denying /internal/* request")
      abortWith 401
      return

  if validTokens.contains(token):
      serviceName = requestContext.getHeaderString("X-Service-Name")  // nullable
      log.info("Internal service call authorized: service={}, path={}", serviceName, path)
      requestContext.setProperty("pulse.internal.authenticated", true)
      return  // allow

  log.warn("Rejected unauthorized internal service call: path={}", path)
  abortWith 401
```

**No OpenFGA check.** The token is the full credential.

### Required change to `AuthorizationFilter`

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/AuthorizationFilter.java`

Add an early-exit check at the top of `filter()`, after the `isExcludedPath()` check:

```java
// If InternalServiceAuthFilter already authenticated this as a service call, skip user authz
if (Boolean.TRUE.equals(requestContext.getProperty("pulse.internal.authenticated"))) {
  log.debug("Skipping user authorization for internal service request: path={}", path);
  return;
}
```

This prevents `AuthorizationFilter` from attempting JWT parsing on the service token for Category A endpoints (which carry `@RequiresPermission(superadmin)`), which would result in 401 from `extractUserIdFromToken()` returning null.

Without this change, a valid service token passes `InternalServiceAuthFilter` but then hits `AuthorizationFilter`'s `extractUserIdFromToken()`, which calls `jwtService.verifyToken()` on the opaque token, throws, returns `null`, and aborts with 401.

### Changes to `ApplicationConfig`

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/config/ApplicationConfig.java`

Add field:
```java
public String internalServiceTokens;  // comma-separated list
```

Add helper:
```java
public Set<String> getInternalServiceTokenSet() {
  if (internalServiceTokens == null || internalServiceTokens.isBlank()) {
    return Set.of();
  }
  return Arrays.stream(internalServiceTokens.split(","))
    .map(String::trim)
    .filter(s -> !s.isEmpty())
    .collect(Collectors.toUnmodifiableSet());
}
```

### Changes to `conf/application-default.conf`

**File:** `backend/server/src/main/resources/conf/application-default.conf`

Add under the `app` block:
```hocon
internalServiceTokens = ${?CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS}
```

### `TenantFilter` — no changes needed

`TenantFilter` already swallows the JWT parse exception silently (line 195: `catch (Exception e) { log.debug(...) }`). An internal call with a raw service token will leave `TenantContext` empty — this is correct; internal endpoints have no tenant scope.

### `RestVerticle` — register the new filter

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/verticle/RestVerticle.java`

Register `InternalServiceAuthFilter` with Resteasy in the same provider registration block as `AuthorizationFilter` and `TenantFilter`.

### `MainModule` — Guice binding (optional)

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/MainModule.java`

No new binding is strictly required if `InternalServiceAuthFilter` uses the same lazy `GuiceInjector.getGuiceInjector().getInstance(ApplicationConfig.class)` pattern. If you prefer eager injection:

```java
bind(InternalServiceAuthFilter.class).in(Singleton.class);
```

---

## 6. OpenFGA / Authorization

No OpenFGA involvement for service-to-service auth. Internal services are infrastructure-level callers, not tenant users.

The `@RequiresPermission(Constants.PERMISSION_SUPERADMIN)` annotations on Category A endpoints use `openFgaService.isSuperAdmin(userId)` — this is the right gate for **human superadmin users** operating through the internal UI. That path is preserved unchanged. It is only bypassed for calls that arrive with a valid service token (via the `pulse.internal.authenticated` request property).

If a future requirement arises to restrict which services can call which endpoints (e.g., alerts-cron may call `/internal/v1/projects/limits/*` but not `/internal/v1/tiers/*`), the right mechanism is a per-token allowed-path set in config, not OpenFGA.

---

## 7. Implementation Steps

Steps are ordered; each depends on the previous.

**Step 1 — Add config field to pulse-server**

- File: `backend/server/src/main/java/org/dreamhorizon/pulseserver/config/ApplicationConfig.java`
- Add `public String internalServiceTokens;`
- Add `getInternalServiceTokenSet()` helper.

**Step 2 — Add HOCON key**

- File: `backend/server/src/main/resources/conf/application-default.conf`
- Add `internalServiceTokens = ${?CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS}` inside the `app {}` block.

**Step 3 — Create `InternalServiceAuthFilter`**

- New file: `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/InternalServiceAuthFilter.java`
- `@Provider`, `@Priority(Priorities.AUTHENTICATION)`, implements `ContainerRequestFilter`.
- Lazy-loads `ApplicationConfig` from `GuiceInjector` (same pattern as `AuthorizationFilter`).
- Logic as described in section 5.
- On success: `requestContext.setProperty("pulse.internal.authenticated", true)`.
- Dev-mode bypass: reads `applicationConfig.googleOAuthEnabled`.

**Step 4 — Modify `AuthorizationFilter`**

- File: `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/AuthorizationFilter.java`
- After the `isExcludedPath()` check (line 61), add:
  ```java
  if (Boolean.TRUE.equals(requestContext.getProperty("pulse.internal.authenticated"))) {
    return;
  }
  ```
- This prevents double-parsing the service token as a user JWT for Category A annotated endpoints.

**Step 5 — Register filter in `RestVerticle`**

- File: `backend/server/src/main/java/org/dreamhorizon/pulseserver/verticle/RestVerticle.java`
- Add `InternalServiceAuthFilter` to the JAX-RS provider registration block.

**Step 6 — Update `ServiceError` (optional, recommended)**

- File: `backend/server/src/main/java/org/dreamhorizon/pulseserver/error/ServiceError.java`
- Add: `INTERNAL_SERVICE_UNAUTHORISED("BE1014", "Unauthorized internal service request", 401)`
- Use in `InternalServiceAuthFilter` abort helper instead of a raw `Response.Status.UNAUTHORIZED`.

**Step 7 — Write tests**

New file: `backend/server/src/test/java/org/dreamhorizon/pulseserver/filter/InternalServiceAuthFilterTest.java`

Required test cases (must achieve ≥80% coverage on the new file):

- `shouldAllowNonInternalPath` — path not starting with `internal/`, filter returns without aborting.
- `shouldDenyInternalPathWithNoAuthHeader` — no `Authorization` header → 401.
- `shouldDenyInternalPathWithNonBearerHeader` — `Authorization: Basic ...` → 401.
- `shouldDenyInternalPathWithEmptyToken` — `Authorization: Bearer ` (empty) → 401.
- `shouldDenyInternalPathWithUnknownToken` — token not in config set → 401.
- `shouldAllowInternalPathWithValidToken` — token matches config set → filter does not abort, sets `pulse.internal.authenticated = true`.
- `shouldAllowAllInternalPathsInDevMode` — `googleOAuthEnabled=false` → filter does not abort regardless of token.
- `shouldDenyWhenNoTokensConfigured` — `internalServiceTokens` is blank → 401.

New test additions in `AuthorizationFilterTest.java`:

- `shouldSkipAuthorizationForInternalServiceRequest` — request property `pulse.internal.authenticated = true` → filter returns without calling `openFgaService`.

**Step 8 — Set env vars in deploy config**

- File: `deploy/` (Docker Compose / ECS task definitions / `.env.example`)
- Add `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS` to pulse-server config.
- Verify `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` is present for pulse-alerts-cron.
- In dev, set both to the same value; in production, inject from AWS Secrets Manager.

**Step 9 — Run verify**

```bash
cd backend/server && mvn verify
```

Fix checkstyle failures (140-char lines, 2-space indent, no wildcards). Confirm JaCoCo passes 80% on `InternalServiceAuthFilter.java` and the modified `AuthorizationFilter.java`.

---

## 8. Adding New Services

To onboard a new service:

1. **Generate a secret**: random 40+ character alphanumeric string. Store in AWS Secrets Manager at `pulse/service-token/<service-name>`.
2. **Inject into pulse-server**: append to `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS`. Redeploy pulse-server.
3. **Inject into calling service**: set as env var and send as `Authorization: Bearer <secret>` on every `/internal/*` call.
4. **Optional `X-Service-Name` header**: `alerts-cron`, `otel-consumer`, etc. Logged by the filter. No pulse-server code change needed.
5. No OpenFGA changes, no MySQL migration, no new Java classes.

### Rotation

1. Add new token to `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS` alongside old (list supports multiple valid tokens simultaneously).
2. Redeploy pulse-server with both tokens active.
3. Update calling service to use new token; redeploy calling service.
4. Remove old token from pulse-server list; redeploy pulse-server.

---

## Summary of Files to Touch

| File | Change |
|---|---|
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/config/ApplicationConfig.java` | Add `internalServiceTokens` field + `getInternalServiceTokenSet()` |
| `backend/server/src/main/resources/conf/application-default.conf` | Add HOCON key for `internalServiceTokens` |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/InternalServiceAuthFilter.java` | **New file** — the filter; sets `pulse.internal.authenticated` on success |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/AuthorizationFilter.java` | Add early-exit check for `pulse.internal.authenticated` request property |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/verticle/RestVerticle.java` | Register `InternalServiceAuthFilter` |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/error/ServiceError.java` | Add `INTERNAL_SERVICE_UNAUTHORISED` (optional) |
| `backend/server/src/test/java/org/dreamhorizon/pulseserver/filter/InternalServiceAuthFilterTest.java` | **New file** — unit tests |
| `backend/server/src/test/java/org/dreamhorizon/pulseserver/filter/AuthorizationFilterTest.java` | Add `shouldSkipAuthorizationForInternalServiceRequest` |
| `deploy/` env config files | Add `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS` to pulse-server |
| `backend/pulse-alerts-cron/src/main/resources/conf/application-default.conf` | No change — `serviceJwtSecret` already wired and sent as Bearer token |
