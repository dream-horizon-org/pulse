# Cron → Server Authentication Flow

## 1. Overview

`pulse-alerts-cron` communicates with `pulse-server` using two distinct patterns depending on the
endpoint being called. For data-sync operations (usage credits, API keys, analytics batches,
usage-limit notifications), the cron service authenticates by attaching a pre-shared service JWT in
the `Authorization: Bearer` header — this token is verified by the server's `TenantFilter` and then
checked against OpenFGA's superadmin relation by `AuthorizationFilter`. For alert-evaluation
requests, the cron service sends no auth credentials at all; those paths are statically excluded
from both filters. The service JWT is **not** obtained via the login flow — it is a long-lived token
configured through an environment variable, shared between both services at deploy time.

---

## 2. The Two Patterns

| | Pattern A — Service JWT | Pattern B — No Auth |
|---|---|---|
| **Used by** | `PeriodicSyncService`, `BatchSchedulerService` via `PulseServerApiClient` | `CronManager` (alert evaluation), `AlertsService` (alert bootstrap) |
| **Target paths** | `POST /internal/v1/projects/limits/sync-to-redis` `POST /internal/v1/api-keys/sync-to-redis` `POST /internal/v1/projects/limits/process-usage-notifications` `POST /internal/analytics/funnels` `POST /internal/analytics/journeys` `POST /internal/analytics/events` | `GET /alerts` `GET /v1/alert/evaluateAndTriggerAlert?alertId=…` |
| **Auth header** | `Authorization: Bearer <serviceJwt>` | None |
| **Server-side check** | `TenantFilter` JWT verify → `AuthorizationFilter` superadmin OpenFGA check | Path excluded in both `TenantFilter` and `AuthorizationFilter` |
| **Endpoint annotation** | `@RequiresPermission("superadmin")` (present on most; see §6 for exceptions) | No annotation; path never reaches filter logic |

---

## 3. Pattern A — Step by Step

### Step 1: Request originates from a Vert.x periodic timer

`PeriodicSyncService` registers three `vertx.setPeriodic(…)` timers at startup. On each tick it
calls into `DataSyncService`, which delegates to `PulseServerApiClient`.

```
// backend/pulse-alerts-cron/src/main/java/.../services/PeriodicSyncService.java : lines 53-72

this.usageLimitsTimerId = vertx.setPeriodic(usageIntervalSec * 1000, id -> {
    executeUsageLimitsSync();                          // → DataSyncService → PulseServerApiClient.syncUsageCreditsToRedis()
});
this.apiKeysTimerId = vertx.setPeriodic(apiKeysIntervalSec * 1000, id -> {
    executeApiKeysSync();                              // → DataSyncService → PulseServerApiClient.syncApiKeysToRedis()
});
this.notificationTimerId = vertx.setPeriodic(notificationIntervalSec * 1000, id -> {
    executeUsageLimitNotifications();                  // → DataSyncService → PulseServerApiClient.processUsageLimitNotifications()
});
```

Default intervals come from `ApplicationConfig`:
- usage credits: **5 s** (`resolveUsageCreditsSyncIntervalSeconds`)
- API keys: **600 s** (`resolveApiKeysSyncIntervalSeconds`)
- notifications: **86400 s** (`resolveUsageLimitNotificationIntervalSeconds`)

`BatchSchedulerService` uses a separate 60-second polling timer and fires at a configured UTC wall
time (default `02:00`). It calls `PulseServerApiClient.triggerFunnelBatch()`,
`triggerJourneyBatch()`, and `triggerEventsBatch()` in sequence.

```
// backend/pulse-alerts-cron/src/main/java/.../services/BatchSchedulerService.java : lines 46-48, 104-111

this.dailyBatchTimerId = vertx.setPeriodic(CHECK_INTERVAL_MS, id -> {   // CHECK_INTERVAL_MS = 60_000
    checkAndExecuteDailyJobs();
});
// ...
apiClient.triggerFunnelBatch()
    .delay(5, TimeUnit.SECONDS)
    .andThen(apiClient.triggerJourneyBatch())
    .delay(5, TimeUnit.SECONDS)
    .andThen(apiClient.triggerEventsBatch())
    .subscribe(…);
```

Both services are started from `MainVerticle.initCrons()`.

```
// backend/pulse-alerts-cron/src/main/java/.../verticle/MainVerticle.java : lines 95-119

private void initCrons() {
    this.initAlertsFromDbOnce();
    this.initPeriodicSync();        // → new PeriodicSyncService(vertx, webClient, applicationConfig).start()
    this.initBatchScheduler();      // → GuiceInjector.getInstance(BatchSchedulerService.class).start()
}
```

---

### Step 2: Credential is configured through an env var

`conf/application-default.conf` maps `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` to the
`serviceJwtSecret` field in `ApplicationConfig`:

```hocon
// backend/pulse-alerts-cron/src/main/resources/conf/application-default.conf : line 4

serviceJwtSecret = ${?CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET}
```

The `?` sigil makes it optional — the field is `null` if the variable is not set. `ApplicationConfig`
is a `@Singleton` deserialized from HOCON by `ConfigUtils.getConfigRetriever()`.

```
// backend/pulse-alerts-cron/src/main/java/.../config/ApplicationConfig.java : line 18

private String serviceJwtSecret;
```

`PulseServerApiClient` reads the credential in its constructor:

```java
// backend/pulse-alerts-cron/src/main/java/.../client/PulseServerApiClient.java : lines 26-31

@Inject
public PulseServerApiClient(WebClient webClient, ApplicationConfig config) {
    this.webClient = webClient;
    this.apiBaseUrl = config.getPulseServerUrl();
    this.serviceJwt = config.getServiceJwtSecret();   // stored as instance field
    this.config = config;
}
```

---

### Step 3: HTTP request is built and sent via Vert.x WebClient

Every outbound call in `PulseServerApiClient` follows the same pattern — `webClient.postAbs(url)`
with `Authorization: Bearer <serviceJwt>` added as a header before `.rxSend()`:

```java
// backend/pulse-alerts-cron/src/main/java/.../client/PulseServerApiClient.java : lines 52-58
// (triggerBatchJob — identical pattern in syncUsageCreditsToRedis, syncApiKeysToRedis,
//  processUsageLimitNotifications)

return Single.defer(() ->
    webClient
        .postAbs(endpoint)
        .putHeader("Authorization", "Bearer " + serviceJwt)
        .putHeader("Content-Type", "application/json")
        .timeout(REQUEST_TIMEOUT_MS)                     // REQUEST_TIMEOUT_MS = 30_000
        .rxSend()
        …
);
```

`REQUEST_TIMEOUT_MS` is 30 000 ms (30 s) for all calls. The `WebClient` instance is created once
in `MainVerticle` with connection-pool settings from `webclient-default.conf`.

---

### Step 4: Server-side — TenantFilter verifies the JWT

`TenantFilter` runs at `@Priority(AUTHENTICATION + 10)`. For `internal/…` paths it is **not**
excluded (note the excluded-path list does not include the `internal/` prefix), so it processes
every Pattern A request.

```java
// backend/server/src/main/java/.../tenant/TenantFilter.java : lines 48, 159-198

private static final String INTERNAL_PATH_PREFIX = "internal/";  // defined but NOT in isExcludedPath()

private void resolveAndSetTenantContext(ContainerRequestContext requestContext) {
    String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    // ... strips "Bearer " prefix
    Claims claims = service.verifyToken(token);   // ← signature + expiry verified here
    String jwtTenantId = claims.get(CLAIM_TENANT_ID, String.class);
    String systemRole   = claims.get(CLAIM_SYSTEM_ROLE, String.class);
    // ... sets TenantContext if tenantId claim is present
}
```

`JwtService.verifyToken()` uses `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token)`.
An expired or tampered token throws an exception here, causing an empty `TenantContext` and likely
a downstream 401 or 403.

```java
// backend/server/src/main/java/.../service/JwtService.java : lines 139-145

public Claims verifyToken(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
}
```

The signing key is derived from `CONFIG_SERVICE_APPLICATION_JWT_SECRET` — the same secret must be
used to create the service JWT that the cron passes in. No separate key exists; both services share
the same HMAC-SHA256 secret.

---

### Step 5: Server-side — AuthorizationFilter checks superadmin in OpenFGA

`AuthorizationFilter` runs at `@Priority(AUTHORIZATION)` (after `TenantFilter`). The `internal/`
path prefix is **not** in the filter's excluded list either, so Pattern A requests are checked.

```java
// backend/server/src/main/java/.../filter/AuthorizationFilter.java : lines 83-98

if (Constants.RELATION_SUPERADMIN.equals(action)) {    // action = "superadmin" from @RequiresPermission
    if (!getOpenFgaService().isEnabled()) {
        log.debug("[DISABLED] Skipping superadmin check for user={}", userId);
        return;
    }
    Boolean isSuperAdmin = getOpenFgaService().isSuperAdmin(userId).blockingGet();
    if (!Boolean.TRUE.equals(isSuperAdmin)) {
        abortForbidden(requestContext, "Access denied: You don't have superadmin permission");
        return;
    }
    return;   // ← granted, request proceeds to controller
}
```

`userId` comes from the `sub` claim of the verified JWT (`extractUserIdFromToken()` at line 174).
The filter calls `isSuperAdmin(userId)` as a synchronous blocking call on the event loop — this is
an existing design choice, not recommended for new code.

The constant `PERMISSION_SUPERADMIN = "superadmin"` is defined at:

```java
// backend/server/src/main/java/.../constant/Constants.java : line 65
public static final String PERMISSION_SUPERADMIN = "superadmin";
```

Controllers that are correctly gated use it via:

```java
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
```

---

## 4. Pattern B — Step by Step (No Auth)

### Alert bootstrap

On startup, `MainVerticle.initAlertsFromDb()` calls `AlertsService.getAlerts()`:

```java
// backend/pulse-alerts-cron/src/main/java/.../services/AlertsService.java : lines 26-30

return webClient
    .getAbs(applicationConfig.getPulseServerUrl() + "/alerts")
    .putHeader(HttpHeaders.CONTENT_TYPE, HEADER_APPLICATION_JSON)
    .putHeader(HttpHeaders.ACCEPT, HEADER_APPLICATION_JSON)
    .rxSend()
```

No `Authorization` header is added. This works because `/alerts` is in both filter excluded lists.

### Alert evaluation

`CronManager.triggerEvaluation()` fires `GET /v1/alert/evaluateAndTriggerAlert?alertId=…` with only
an `X-Project-ID` header:

```java
// backend/pulse-alerts-cron/src/main/java/.../services/CronManager.java : lines 140-144

return webClient
    .getAbs(url)
    .putHeader("X-Project-ID", projectId)
    .timeout(REQUEST_TIMEOUT_MS)
    .rxSend()
```

No `Authorization` header is set. The path `alerts/…` matches the excluded prefix in both filters:

```java
// backend/server/src/main/java/.../filter/AuthorizationFilter.java : line 48
private static final String ALERTS_PATH_PREFIX = "alerts";
// line 145 → normalizedPath.startsWith(ALERTS_PATH_PREFIX)

// backend/server/src/main/java/.../tenant/TenantFilter.java : line 56
private static final String ALERTS_PATH_PREFIX = "alerts";
// line 102 → normalizedPath.startsWith(ALERTS_PATH_PREFIX)
```

Both filters return immediately for any path that begins with `alerts`, meaning no JWT verification
and no OpenFGA check occurs for these endpoints.

---

## 5. The Service JWT Token

The service JWT is not issued by the login flow. It is a pre-computed HS256 token signed with the
same `CONFIG_SERVICE_APPLICATION_JWT_SECRET` used by `pulse-server`. At startup the cron loads it
from `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` via HOCON into `ApplicationConfig.serviceJwtSecret`.

The token **must** carry a `sub` claim identifying a user that OpenFGA recognises as a
`superadmin` on `system:pulse`. Minimally required claims for Pattern A endpoints:

```json
{
  "sub":        "<userId registered as superadmin in OpenFGA>",
  "email":      "service@internal",
  "type":       "access",
  "systemRole": "superadmin",
  "iat":        <issued-at-epoch>,
  "exp":        <expiry-epoch>
}
```

`TenantFilter` looks for a `tenantId` claim to set `TenantContext`. For internal endpoints
`TenantContext` is not consumed by the controllers, so the absence of `tenantId` is harmless but
note that `TenantContext.getTenantId()` will return `null` in any service called downstream.

`JwtService.generateAccessToken(userId, email, name, tenantId, systemRole)` is the server-side
method used to produce tokens with `systemRole`.

```java
// backend/server/src/main/java/.../service/JwtService.java : lines 87-112
```

Validity of the token in production is 24 hours (`ACCESS_TOKEN_VALIDITY_MS = 86400000L`). Because
the cron holds the raw secret string rather than going through token refresh, the ops team must
rotate the token and redeploy the cron when it expires.

---

## 6. Security Note — Two Sync Endpoints Without `@RequiresPermission`

`POST /internal/v1/projects/limits/sync-to-redis` and
`POST /internal/v1/projects/limits/process-usage-notifications` in `InternalUsageLimitsController`
do **not** carry `@RequiresPermission`:

```java
// backend/server/src/main/java/.../resources/usagelimits/InternalUsageLimitsController.java

@POST
@Path("/limits/sync-to-redis")
// ← no @RequiresPermission
public CompletionStage<Response<CronRedisSyncJobAcceptedRestResponse>> syncUsageCreditsToRedis() { … }

@POST
@Path("/limits/process-usage-notifications")
// ← no @RequiresPermission
public CompletionStage<Response<CronRedisSyncJobAcceptedRestResponse>> processUsageLimitNotifications() { … }
```

`AuthorizationFilter.getRequiresPermission()` returns `null` for these methods, and the filter
logs `"No @RequiresPermission annotation for path, skipping authorization"` and returns without
checking OpenFGA. This means **any caller with a network path to pulse-server can trigger these
operations without authentication** — neither a valid JWT nor superadmin status is enforced.

The three analytics endpoints in `InternalAnalyticsController` (`/internal/analytics/funnels`,
`/internal/analytics/journeys`, `/internal/analytics/events`) have the same gap.

Compare with the correctly gated endpoints in the same controller:

```java
// InternalUsageLimitsController.java : lines 67-68
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
public CompletionStage<Response<ProjectUsageLimitRestResponse>> getProjectLimits(…)

// InternalApiKeysController.java : lines 44-45
@RequiresPermission(Constants.PERMISSION_SUPERADMIN)
public CompletionStage<Response<ValidApiKeyListRestResponse>> getAllValidApiKeys()
```

The fix is to add `@RequiresPermission(Constants.PERMISSION_SUPERADMIN)` to each of the five
unguarded methods, and ensure `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` is set to a token
whose `sub` maps to an OpenFGA superadmin.

---

## 7. Key Files Table

| File | Lines of interest | What it does |
|------|-------------------|--------------|
| `backend/pulse-alerts-cron/src/main/resources/conf/application-default.conf` | 4 | Maps `CONFIG_SERVICE_APPLICATION_SERVICEJWTSECRET` env var to `serviceJwtSecret` field |
| `backend/pulse-alerts-cron/src/main/java/.../config/ApplicationConfig.java` | 18, 15 | HOCON-deserialized config; holds `serviceJwtSecret` and `pulseServerUrl` |
| `backend/pulse-alerts-cron/src/main/java/.../client/PulseServerApiClient.java` | 26-31, 52-58, 81-115, 121-144, 150-195 | Builds all HTTP requests to pulse-server; injects `Authorization: Bearer` header |
| `backend/pulse-alerts-cron/src/main/java/.../services/DataSyncService.java` | 18-77 | Thin wrapper: delegates each sync operation to `PulseServerApiClient` |
| `backend/pulse-alerts-cron/src/main/java/.../services/PeriodicSyncService.java` | 43-73 | Registers Vert.x periodic timers; guards against overlapping runs with `AtomicBoolean` |
| `backend/pulse-alerts-cron/src/main/java/.../services/BatchSchedulerService.java` | 36-51, 97-118 | 60-second polling timer; fires batch jobs at configured UTC wall time |
| `backend/pulse-alerts-cron/src/main/java/.../services/CronManager.java` | 98-125, 138-144 | Alert evaluation: fires GET requests with `X-Project-ID` only, no auth |
| `backend/pulse-alerts-cron/src/main/java/.../services/AlertsService.java` | 26-30 | Bootstrap GET `/alerts` — no auth header |
| `backend/pulse-alerts-cron/src/main/java/.../verticle/MainVerticle.java` | 95-119, 127-149 | Entry point: starts `PeriodicSyncService`, `BatchSchedulerService`, and `CronManager` |
| `backend/server/src/main/java/.../tenant/TenantFilter.java` | 38, 90-107, 159-198 | `@Priority(AUTHENTICATION+10)`: verifies JWT, sets `TenantContext`; excludes `alerts/` prefix |
| `backend/server/src/main/java/.../filter/AuthorizationFilter.java` | 39, 58-127, 132-147 | `@Priority(AUTHORIZATION)`: reads `@RequiresPermission`, calls `isSuperAdmin` for `"superadmin"` action; excludes `alerts/` |
| `backend/server/src/main/java/.../filter/RequiresPermission.java` | 1-22 | Annotation that `AuthorizationFilter` reads to determine required action |
| `backend/server/src/main/java/.../service/JwtService.java` | 36-54, 139-145, 87-112 | Derives HMAC-SHA256 signing key from `jwtSecret`; `verifyToken()` used by both filters |
| `backend/server/src/main/java/.../constant/Constants.java` | 65, 116 | `PERMISSION_SUPERADMIN = "superadmin"`, `RELATION_SUPERADMIN = "superadmin"` |
| `backend/server/src/main/java/.../resources/usagelimits/InternalUsageLimitsController.java` | 107-115, 122-131 | `sync-to-redis` and `process-usage-notifications` — missing `@RequiresPermission` |
| `backend/server/src/main/java/.../resources/apikeys/InternalApiKeysController.java` | 60-69 | `sync-to-redis` — missing `@RequiresPermission` |
| `backend/server/src/main/java/.../resources/analytics/InternalAnalyticsController.java` | 40-78 | Three analytics batch endpoints — all missing `@RequiresPermission` |
