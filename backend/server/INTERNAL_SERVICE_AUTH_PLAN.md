# Service-to-Service Auth: pulse-alerts-cron ↔ pulse-server

## Context

pulse-alerts-cron calls 6 `/internal/*` endpoints on pulse-server. Today it sends `Authorization: Bearer <raw-opaque-string>` — not a signed JWT. Five of those endpoints have no `@RequiresPermission` annotation at all (effectively open to any network caller). Two more lack auth headers entirely (the alert-evaluation path).

**Goal:** Enforce a clean auth boundary at the `/internal/*` prefix so:
1. Cron calls are authenticated (service identity, not user identity)
2. All 6 cron-called endpoints require authorization
3. The user JWT filter chain doesn't choke on an opaque service token

**Chosen pattern: Pattern B — InternalServiceAuthFilter (opaque token)**

Pattern A (pre-signed JWT mapped to OpenFGA superadmin) was considered but rejected:
- Requires a synthetic user row + OpenFGA superadmin tuple for a machine identity
- Long-lived JWTs need rotation; expiry breaks cron silently at runtime
- Pollutes user auth model with a service account
- Pattern B (opaque token + dedicated filter) is already the team's documented direction in `SERVICE_AUTH_PLAN.md`

---

## Files to Modify

### pulse-server

| File | Change |
|---|---|
| `backend/server/src/main/resources/conf/application-default.conf` | Add `internalServiceTokens` HOCON key |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/config/ApplicationConfig.java` | Add `internalServiceTokens` field |
| **NEW** `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/InternalServiceAuthFilter.java` | Dedicated filter for `/internal/*` |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/AuthorizationFilter.java` | Add early-exit guard (line ~61) |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/filter/TenantFilter.java` | Add same early-exit guard (prevents JWT parse of opaque token) |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/verticle/RestVerticle.java` | Register `InternalServiceAuthFilter` |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/apikeys/InternalApiKeysController.java` | Add `@RequiresPermission` to `sync-to-redis` (line 60) |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/usagelimits/InternalUsageLimitsController.java` | Add `@RequiresPermission` to 2 endpoints (lines 106, 121) |
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/analytics/InternalAnalyticsController.java` | Add `@RequiresPermission` to 3 endpoints (lines 40, 55, 70) |

### pulse-alerts-cron

| File | Change |
|---|---|
| `backend/pulse-alerts-cron/src/main/java/org/dreamhorizon/pulsealertscron/client/PulseServerApiClient.java` | No code change needed — already sends `Bearer <serviceJwt>` on lines 55, 87, 127, 156 |

---

## Implementation Steps

### Step 1 — ApplicationConfig: add internalServiceTokens

**`application-default.conf`** — add after existing `jwtSecret` line:
```hocon
internalServiceTokens = ""
internalServiceTokens = ${?CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS}
```

**`ApplicationConfig.java`** — add field:
```java
private String internalServiceTokens;  // comma-separated opaque tokens
```

Add helper method (or parse in filter):
```java
public List<String> getInternalServiceTokenList() {
    if (internalServiceTokens == null || internalServiceTokens.isBlank()) return List.of();
    return Arrays.asList(internalServiceTokens.split(","));
}
```

---

### Step 2 — NEW InternalServiceAuthFilter

**Path:** `backend/server/src/main/java/.../filter/InternalServiceAuthFilter.java`

```java
@Priority(Priorities.AUTHENTICATION)
@Provider
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InternalServiceAuthFilter implements ContainerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "internal/";
    static final String PROP_INTERNAL_AUTHENTICATED = "pulse.internal.authenticated";

    private final ApplicationConfig applicationConfig;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) return;

        // Dev mode: bypass
        if (!applicationConfig.isGoogleOauthEnabled()) {
            requestContext.setProperty(PROP_INTERNAL_AUTHENTICATED, true);
            return;
        }

        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        String token = authHeader.substring(7);
        List<String> allowedTokens = applicationConfig.getInternalServiceTokenList();
        boolean valid = allowedTokens.stream()
            .anyMatch(t -> MessageDigest.isEqual(t.getBytes(StandardCharsets.UTF_8),
                                                  token.getBytes(StandardCharsets.UTF_8)));
        if (!valid) {
            log.warn("InternalServiceAuthFilter: rejected invalid service token");
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        requestContext.setProperty(PROP_INTERNAL_AUTHENTICATED, true);
    }
}
```

`MessageDigest.isEqual` provides constant-time comparison to prevent timing attacks.

---

### Step 3 — AuthorizationFilter: early-exit guard

In `AuthorizationFilter.java`, after `isExcludedPath()` check (around line 61), add:

```java
if (Boolean.TRUE.equals(requestContext.getProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED))) {
    return;
}
```

---

### Step 4 — TenantFilter: same guard

In `TenantFilter.java`, near the top of `filter()`, before any JWT extraction:

```java
if (Boolean.TRUE.equals(requestContext.getProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED))) {
    return;
}
```

Prevents TenantFilter from attempting to parse the opaque service token as a user JWT → 401.

---

### Step 5 — RestVerticle: register filter

Add `InternalServiceAuthFilter.class` to the JAX-RS application or Vert.x resource registration list alongside existing filters.

---

### Step 6 — Add @RequiresPermission to 5 unprotected endpoints

Defense-in-depth (user auth path backup even if service filter is primary guard):

**`InternalApiKeysController.java` line 60** — `POST /internal/v1/api-keys/sync-to-redis`:
```java
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
```

**`InternalUsageLimitsController.java` line 106** — `POST /internal/v1/projects/limits/sync-to-redis`:
```java
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
```

**`InternalUsageLimitsController.java` line 121** — `POST /internal/v1/projects/limits/process-usage-notifications`:
```java
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
```

**`InternalAnalyticsController.java` lines 40, 55, 70** — funnels, journeys, events:
```java
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
```

---

### Step 7 — Token value / secret management

**Token format:** Opaque random string, min 32 chars (e.g., `openssl rand -hex 32`)

**Environment variables:**
| Service | Env var | Value |
|---|---|---|
| pulse-server | `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS` | `<token>` (comma-separated, supports multiple callers) |
| pulse-alerts-cron | `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` | `<same-token>` |

**Production:** AWS Secrets Manager at `pulse/service-token/alerts-cron`; inject into both services at deploy time.

**Dev/test:** Static value e.g. `dev_internal_service_token_01` in both `application-default.conf` files.

---

## Existing Wiring That Does Not Change

- `PulseServerApiClient` already sends `Authorization: Bearer <serviceJwt>` on all 4 data-sync calls — no code change, only config value swap
- Alert-evaluation endpoints (`GET /alerts`, `GET /v1/alert/evaluateAndTriggerAlert`) remain excluded via existing `"alerts"` prefix exclusion in AuthorizationFilter — `CronManager` and `AlertsService` do not need auth headers for those paths

---

## Verification

1. **Unit tests — InternalServiceAuthFilter:**
   - Valid token → sets `pulse.internal.authenticated = true`, does not abort
   - Invalid token → 401
   - Missing header → 401
   - Non-internal path → no-op
   - Dev mode (`GOOGLE_OAUTH_ENABLED=false`) → auto-allow regardless of token

2. **Integration test:**
   - Start server with `CONFIG_SERVICE_APPLICATION_INTERNAL_SERVICE_TOKENS=test-token`
   - `POST /internal/v1/api-keys/sync-to-redis` with `Bearer test-token` → 200
   - Same endpoint with `Bearer wrong-token` → 401
   - Same endpoint with no Authorization header → 401

3. **Regression:**
   - `cd backend/server && mvn verify` — existing tests must pass
   - Confirm TenantFilter guard doesn't break tenant resolution for normal user requests

4. **Manual smoke test:**
   - `cd deploy && ./scripts/start.sh -d`
   - `cd deploy && ./scripts/logs.sh server`
   - Confirm cron sync calls succeed (no 401s in cron logs)
