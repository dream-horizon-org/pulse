# 03.1 — Interaction Config Fetcher

**Goal:** Fetch and cache the interaction definition JSON at SDK init, refresh in the background every 30 minutes, and expose the config to the matching engine (03.2).

**File:** `src/interactions/config-fetcher.ts`  
**Android equivalent:** `PulseEndpointUtils.getInteractionConfigUrl()` + `InteractionConfigRestFetcher.kt` — port with `fetch()` replacing Retrofit

---

## Config URL Strategy

| Environment | URL | Auth |
|---|---|---|
| **Local / dev ingest** | `{endpointBaseUrl-derived backendUrl}/v1/interaction-configs/` | `X-API-KEY` |
| **Prod** | `https://pulse-otel-collector.pulse-ux.com/config/projects/{projectId}/interaction-config.json` | None |

The resolver chooses **local vs prod using the API key**, mirroring Android’s `isApiLocalDev(apiKey)` signal:

- **Local/dev keys:** `isLocalEnvironment(apiKey)` is `true` (same regex family as Android: `default-project_*` and `Test-*_*`).
- **Prod keys:** `isLocalEnvironment(apiKey)` is `false`.

For the **local branch**, the REST base URL is still derived from the browser’s resolved OTLP `endpointBaseUrl` (typically `http://localhost:4318`) by rewriting `:4318 → :8080` (same pattern used elsewhere in the web SDK for “collector URL → backend URL”).

Both branches return the **same JSON schema**: a flat JSON array of `InteractionConfig` objects.

> **Note (browser vs Android):** Android hardcodes `http://10.0.2.2:8080/...` for emulator REST. Web uses the browser’s `endpointBaseUrl` to compute the backend host, but the **local/prod decision** is keyed off the **API key** (Android parity).

---

## Config Schema

The server delivers a **JSON array** of `InteractionConfig` objects (not wrapped in a container object):

```typescript
// Types live in src/interactions/interaction-models.ts

type PropertyOperator = 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'NOT_CONTAINS' | 'STARTS_WITH' | 'ENDS_WITH';

interface PropertyFilter {
  key: string;
  value: string;
  operator: PropertyOperator;
}

interface InteractionEvent {
  name: string;                      // Must match PulseWeb.trackEvent() call
  required: boolean;                 // false = optional step
  isBlacklisted?: boolean;           // per-event blacklist flag
  props?: PropertyFilter[];          // property filters (AND logic)
}

interface InteractionConfig {
  id: string;
  name: string;
  events: InteractionEvent[];
  thresholdInMs: number;             // inter-step timeout (not whole-flow)
  uptimeLowerLimitInMs: number;      // ≤ this → Excellent
  uptimeMidLimitInMs: number;        // ≤ this → Good
  uptimeUpperLimitInMs: number;      // ≤ this → Average; above → Poor
  globalBlacklistedEvents: string[]; // event names that reset any ongoing match
}
```

### Example Config (JSON array)

```json
[
  {
    "id": "checkout_flow",
    "name": "Checkout Flow",
    "thresholdInMs": 5000,
    "uptimeLowerLimitInMs": 2000,
    "uptimeMidLimitInMs": 5000,
    "uptimeUpperLimitInMs": 10000,
    "globalBlacklistedEvents": ["cancel_checkout", "session_timeout"],
    "events": [
      { "name": "cart_viewed",      "required": true },
      { "name": "promo_applied",    "required": false },
      { "name": "checkout_started", "required": true,
        "props": [{ "key": "channel", "value": "organic", "operator": "EQUALS" }] },
      { "name": "payment_entered",  "required": false },
      { "name": "order_placed",     "required": true }
    ]
  }
]
```

---

## Implementation (current code shape)

The implementation is split into:

1. **`resolveInteractionConfigRequest(endpointBaseUrl, { apiKey })`**
   - Computes `{ enabled, url, headers }` for the fetcher.
   - **Local vs prod** uses `isLocalEnvironment(apiKey)` (Android `isApiLocalDev` parity).
   - **Prod** uses `PULSE_PROD_ENDPOINT_URL` exported from `src/config.ts` (same host string as Android `PULSE_ENDPOINT_URL`).
2. **`InteractionConfigFetcher`**
   - `init()` loads cache (only when enabled), fetches, persists, schedules refresh.
   - Validates JSON shape before calling `setConfigs()` / writing cache.
   - `destroy()` clears the refresh timer.

Key behaviors:

- **SSR-safe:** no `localStorage` / `fetch` on `typeof window === 'undefined'`.
- **Soft failure:** network / non-2xx responses log a warning and keep cached configs (if any).
- **Schema validation:** invalid payloads are ignored (prevents poisoning trackers with partial objects).

---

## Config URL Construction (reference)

```typescript
// src/interactions/config-fetcher.ts (conceptual)

import { PULSE_PROD_ENDPOINT_URL, isLocalEnvironment } from '../config';
import { extractProjectId } from '../resource';

export function resolveInteractionConfigRequest(
  endpointBaseUrl: string,
  config: { apiKey: string },
) {
  if (isLocalEnvironment(config.apiKey)) {
    const backendUrl = endpointBaseUrl.replace(':4318', ':8080').replace(/\/$/, '');
    return {
      enabled: true,
      url: `${backendUrl}/v1/interaction-configs/`,
      headers: { 'X-API-KEY': config.apiKey },
    };
  }

  const projectId = extractProjectId(config.apiKey);
  return {
    enabled: true,
    url: `${PULSE_PROD_ENDPOINT_URL}/config/projects/${projectId}/interaction-config.json`,
    headers: {},
  };
}
```

---

## Edge Cases

| Case | Handling |
|---|---|
| First load — no cache, network slow | Matching is a no-op until a valid config arrives |
| Corrupt localStorage cache | Parse/validation rejects it; fetch may replace |
| localStorage blocked (private browsing, storage quota) | Caught and ignored; in-memory still works for the current page |
| Prod returns 404 | Treated as empty array `[]` after JSON parse (coordinator has no trackers) |
| Prod returns 5xx | Logs warning; keeps using cached config (if any) |
| Config changes between refreshes | `onChange` listeners receive the new array; in-flight matching behavior is owned by the coordinator (03.2) |
| SSR (server-side rendering) | No `localStorage` access without `window` |

---

## Testing

### Unit Tests (Vitest)

Prefer constructing the fetcher with a **mock `fetch`** injected via `new InteractionConfigFetcher(request, mockFetch)` (the real constructor supports this for tests).

Minimum cases:

- loads configs from cache on `init()` (local mode)
- calls `onChange` when fresh config arrives
- handles fetch failure without throwing (keeps stale cache)
- rejects invalid JSON shape (does not call `onChange` with garbage)
- `destroy()` clears refresh timer (no timeout callbacks after shutdown)

---

## Done Criteria

- [ ] **Local ingest:** config fetched from REST (`/v1/interaction-configs/` + `X-API-KEY`) when `isLocalEnvironment(apiKey)` is true (Android `isApiLocalDev` parity)
- [ ] **Prod:** config fetched from `https://pulse-otel-collector.pulse-ux.com/config/projects/{projectId}/interaction-config.json` (Android `PulseEndpointUtils` prod branch parity)
- [ ] Response parsed as `InteractionConfig[]` (flat JSON array, not wrapped object)
- [ ] Cached config loaded immediately from `localStorage` before network round-trip (**only when fetcher is enabled**)
- [ ] Fresh config saved to `localStorage` after successful fetch
- [ ] `onChange` listeners notified when new config arrives (receives `InteractionConfig[]`)
- [ ] Network errors do not throw — fall back silently to cached config
- [ ] `localStorage` errors do not throw (private browsing, quota exceeded)
- [ ] Config refresh scheduled every 30 minutes
- [ ] `destroy()` clears the refresh timer
- [ ] SSR guard: no `localStorage` or `fetch` access when `typeof window === 'undefined'`
- [ ] All unit tests passing
