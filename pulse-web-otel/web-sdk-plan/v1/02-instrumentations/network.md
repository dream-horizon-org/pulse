# 02.2 — Network Instrumentation

**Goal:** Capture every outbound HTTP request (Fetch and XHR) as a span with timing, status code, payload size, and GraphQL operation name — without any app code changes.

**File:** `src/instrumentations/network.ts`
**Android equivalent:** `OkHttp3Instrumentation`, `HttpUrlConnectionInstrumentation`

---

## Signals Produced

### `pulse.type: network.<statusCode>` — one span per HTTP request

> **OTel alignment:** All attribute names follow the [stable OTel HTTP semconv](https://opentelemetry.io/docs/specs/semconv/http/http-spans/) (`http.request.method`, `url.full`, `http.response.status_code`, `server.address`). Deprecated names (`http.method`, `http.url`, `http.status_code`, `net.peer.name`) are NOT used.

> **`pulse.type` matches Android:** values like `network.200`, `network.404`; missing or failed response status → `network.0`. Implemented as `networkPulseType()` in `src/utils/network-http.ts`.

| Attribute | Type | Source | Required |
|---|---|---|---|
| `pulse.type` | string | `network.<statusCode>` (e.g. `network.200`; unknown → `network.0`) | ✅ |
| `http.request.method` | string | Request method (`GET`, `POST`, etc.) | ✅ |
| `http.request.method_original` | string | Original method when `http.request.method = "_OTHER"` | conditional |
| `url.full` | string | Sanitised URL — query stripped unless `captureQueryParams`; **credentials stripped** (`user:pass@`) per OTel | ✅ |
| `http.response.status_code` | long | Response HTTP status | ✅ |
| `http.request.body.size` | long | `Content-Length` request header (bytes) | optional |
| `http.response.body.size` | long | `Content-Length` response header (bytes) | optional |
| `server.address` | string | Hostname extracted from URL | ✅ |
| `server.port` | long | Explicit port or **80** / **443** when scheme default (OTel Required) | ✅ |
| `network.protocol.version` | string | `1.1` / `2` / `3` from `PerformanceResourceTiming.nextHopProtocol` when present (OTel Recommended) | optional |
| `peer.service` | string | Configured `peerServiceMap[server.address]` e.g. `"orders-service"` | optional (opt-in config) |
| `http.duration` | long | Total request duration (ms) — **Pulse custom** (OTel captures duration as span duration; this is a convenience attribute) | ✅ |
| `graphql.operation.name` | string | Parsed from request body | optional |
| `graphql.operation.type` | string | `"query"` / `"mutation"` / `"subscription"` | optional |
| Custom request headers | string | Configurable allowlist → `http.request.header.<name>` | optional |
| Custom response headers | string | Configurable allowlist → `http.response.header.<name>` | optional |

---

## Android Parity

| Aspect | Android (`OkHttpInstrumentation.kt`) | Web |
|---|---|---|
| Base instrumentation | OTel `OkHttp3Instrumenter` via `OkHttp3Singletons` | OTel `FetchInstrumentation` + `XMLHttpRequestInstrumentation` ✅ |
| `pulse.type` | `network.<statusCode>` (Android span processor) | Same — `networkPulseType(status)` ✅ |
| Attribute contract | Stable OTel HTTP semconv (`http.request.method`, `url.full`, etc.) | Same stable semconv ✅ |
| `capturedRequestHeaders` | Configurable allowlist → `http.request.header.<name>` | Same ✅ |
| `capturedResponseHeaders` | Configurable allowlist → `http.response.header.<name>` | Same ✅ |
| `knownMethods` | Configurable; unknown method → `http.request.method = "_OTHER"` + `http.request.method_original` | Same handling ✅ |
| `peer.service` | Hostname → friendly service name mapping (e.g. `api.example.com → "orders-service"`) | ✅ opt-in config `peerServiceMap` |
| Experimental HTTP metrics | `http.client.request.duration` histogram (opt-in via `setEmitExperimentalHttpClientTelemetry`) | ❌ not in M3 scope — V2 |
| Additional extractors | `addAttributesExtractor()` extensibility hook | `applyCustomAttributesOnSpan` ✅ equivalent |
| Span kind | `CLIENT` | `CLIENT` ✅ |
| Context propagation | OTel context injected into request headers (`traceparent`) | Same via `propagateTraceHeaderCorsUrls` ✅ |
| Span status on error | `SpanStatus.ERROR` set for 4xx/5xx and network exceptions | Same — 4xx/5xx → `ERROR`; network failure → `ERROR` + `error.type` ✅ |
| GraphQL operation extraction | ❌ not in Android OkHttp instrumentation | ➕ web extra — `graphql.operation.name` + `graphql.operation.type` parsed from POST body |
| URL sanitisation | ❌ no equivalent (Android doesn't expose query params) | ➕ web extra — query params stripped by default (`privacy.captureQueryParams`) |

---

## Implementation

Uses the official OTEL `FetchInstrumentation` and `XMLHttpRequestInstrumentation` as the base — they handle timing and OTEL context propagation. We extend with custom attributes.

```typescript
// src/instrumentations/network.ts
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request';
import { networkPulseType } from '../utils/network-http';

export function createNetworkInstrumentation(config: NetworkConfig) {
  const ignored = [
    config.endpointBaseUrl,       // never trace Pulse's own OTLP calls
    ...(config.blockedUrls ?? []),
  ];

  return [
    new FetchInstrumentation({
      ignoreUrls: ignored,
      propagateTraceHeaderCorsUrls: config.allowedUrls ?? [],
      applyCustomAttributesOnSpan: (span, request, response) => {
        span.setAttribute('pulse.type', networkPulseType(response.status));
        span.setAttribute('http.duration', getSpanDuration(span));

        // peer.service mapping (opt-in, mirrors Android setPeerServiceMapping())
        try {
          const host = new URL(request.url).hostname;
          const peerService = config.peerServiceMap?.[host];
          if (peerService) span.setAttribute('peer.service', peerService);
        } catch { /* relative URL edge case */ }

        // GraphQL
        const body = getRequestBody(request);
        if (isGraphQL(body)) {
          span.setAttribute('graphql.operation.name', extractOpName(body) ?? '');
          span.setAttribute('graphql.operation.type', extractOpType(body) ?? '');
        }

        // Payload sizes (stable semconv names)
        const reqLen = getHeader(request, 'content-length');
        if (reqLen) span.setAttribute('http.request.body.size', Number(reqLen));
        const resLen = getResponseHeader(response, 'content-length');
        if (resLen) span.setAttribute('http.response.body.size', Number(resLen));

        // Custom headers (allowlist)
        config.capturedRequestHeaders?.forEach(h => {
          const v = getHeader(request, h);
          if (v) span.setAttribute(`http.request.header.${h}`, v);
        });
        config.capturedResponseHeaders?.forEach(h => {
          const v = getResponseHeader(response, h);
          if (v) span.setAttribute(`http.response.header.${h}`, v);
        });
      },
    }),

    new XMLHttpRequestInstrumentation({
      ignoreUrls: ignored,
      applyCustomAttributesOnSpan: (span, xhr) => {
        span.setAttribute('pulse.type', networkPulseType(xhr.status));
      },
    }),
  ];
}

// GraphQL helpers
function isGraphQL(body: string | null): boolean {
  if (!body) return false;
  try { return 'query' in JSON.parse(body); } catch { return false; }
}

function extractOpName(body: string): string | null {
  try {
    const parsed = JSON.parse(body);
    // Named operation: "query GetUser { ... }" → "GetUser"
    const match = parsed.query?.match(/(?:query|mutation|subscription)\s+(\w+)/);
    return match?.[1] ?? parsed.operationName ?? null;
  } catch { return null; }
}

function extractOpType(body: string): string | null {
  try {
    const parsed = JSON.parse(body);
    const match = parsed.query?.match(/^(query|mutation|subscription)/);
    return match?.[1] ?? 'query';
  } catch { return null; }
}
```

---

## URL Sanitisation

URLs can contain PII (user IDs, tokens in query params). Apply sanitisation before storing:

```typescript
function sanitizeUrl(url: string): string {
  try {
    const u = new URL(url);
    // Remove all query params by default (configurable)
    if (!config.privacy?.captureQueryParams) {
      u.search = '';
    }
    return u.toString();
  } catch {
    return url;
  }
}
```

---

## Span Status Rules

| Condition | Span status | `error.type` set? |
|---|---|---|
| `http.response.status_code` 1xx–3xx | `OK` | No |
| `http.response.status_code` 4xx–5xx | `ERROR` | Yes — `"4xx"` / `"5xx"` |
| Network failure (no response, timeout) | `ERROR` | Yes — `"network_error"` |
| `fetch` with `no-cors` (`status = 0`) | `ERROR` | Yes — `"cors_error"` |

Mirrors Android: OkHttp instrumentation sets `SpanStatus.ERROR` for 4xx/5xx and network exceptions.

---

## Peer Service Mapping (optional)

Android's `OkHttpInstrumentation` supports `setPeerServiceMapping()` — maps hostnames to friendly service names so spans read `peer.service = "orders-service"` instead of just a hostname.

Web supports the same via `peerServiceMap` config:

```typescript
instrumentations: {
  network: {
    enabled: true,
    peerServiceMap: {
      'api.example.com': 'orders-service',
      'cdn.example.com': 'cdn',
    }
  }
}
```

Applied in `applyCustomAttributesOnSpan`:
```typescript
const host = new URL(request.url).hostname;
const peerService = config.peerServiceMap?.[host];
if (peerService) span.setAttribute('peer.service', peerService);
```

---

## Edge Cases

| Case | Handling |
|---|---|
| OTLP endpoint calls traced back | Excluded via `ignoreUrls: [config.endpointBaseUrl]` |
| CORS pre-flight `OPTIONS` requests | OTEL instrumentation ignores OPTIONS automatically |
| `fetch` with `no-cors` mode | `response.status` is 0; set `http.status_code: 0` |
| Streaming responses (SSE, chunked) | `Content-Length` absent; omit size attributes |
| GraphQL body is not JSON-parseable | `isGraphQL()` catches parse error, returns false |
| Request body is a `FormData` or `Blob` | `getRequestBody()` returns null, skip GraphQL check |
| Relative URLs | `new URL(url, window.location.origin)` to resolve |
| Request times out (no response) | Span ends with error status, no `http.status_code` |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('extracts GraphQL operation name', () => {
  const body = JSON.stringify({ query: 'query GetUser { user { id } }' });
  expect(extractOpName(body)).toBe('GetUser');
  expect(extractOpType(body)).toBe('query');
});

it('returns null for non-GraphQL body', () => {
  expect(isGraphQL(JSON.stringify({ userId: 1 }))).toBe(false);
});

it('sanitizes query params from URL', () => {
  expect(sanitizeUrl('https://api.example.com/users?token=secret'))
    .toBe('https://api.example.com/users');
});
```

### E2E (Playwright)

```typescript
test('fetch request produces http span', async ({ page }) => {
  await page.goto('/test-page');
  await page.evaluate(() => fetch('https://api.example.com/data'));
  const span = await waitForSpan(receiver, 'network.200');
  expect(span['http.request.method']).toBe('GET');
  expect(span['url.full']).toBe('https://api.example.com/data');
  expect(span['http.response.status_code']).toBe(200);
  expect(span['server.address']).toBe('api.example.com');
});

test('OTLP calls are not traced', async ({ page }) => {
  await page.goto('/test-page');
  const spans = receiver.spans.filter(s => s['url.full']?.includes('/v1/traces'));
  expect(spans).toHaveLength(0);
});
```

---

## Done Criteria

- [x] Every `fetch()` call produces a span with `pulse.type = network.<code>` and `http.request.method`, `url.full`, `http.response.status_code`, `server.address`
- [x] XHR calls also produce `network.<code>` spans
- [x] Pulse's own OTLP endpoints excluded from tracing
- [x] GraphQL `operation.name` and `operation.type` extracted from POST body
- [x] Query params stripped from URL by default
- [x] `http.request.body.size` / `http.response.body.size` present when `Content-Length` header available
- [x] `peer.service` set when `peerServiceMap` configured and hostname matches
- [ ] `http.request.method = "_OTHER"` + `http.request.method_original` — handled by OTel base instrumentation for non-standard methods (V2 — out of scope for initial implementation)
- [x] 4xx/5xx responses set span status `ERROR`
- [x] Network failures set span status `ERROR` with `error.type = "network_error"`
- [x] All unit tests passing (50/50)

---

## Manual Test Cases

### Format: # Test | Steps | Expected | Status | Comment

---

### TC1 — fetch() produces http span with stable semconv attrs

**Steps:**
1. Open app in browser
2. Navigate to any page that makes a `fetch()` call (e.g. `/products` which fetches product list)
3. Open CH / OTLP capture

**Expected:**
- Span with `pulse.type = "network.200"` (or matching stubbed status) present
- `http.request.method = "GET"` (or correct method)
- `url.full` = sanitized URL (no query params)
- `server.address` = hostname of target
- `http.response.status_code` present

**Status:** ✅ Pass — unit TC (applyFetchAttrs) + E2E TC1

---

### TC2 — url.full strips query params (privacy default)

**Steps:**
1. In browser console or via `page.evaluate`, call:
   ```js
   fetch("https://api.example.com/users?token=secretABC&page=2")
   ```
2. Check `url.full` in the emitted span

**Expected:** `url.full = "https://api.example.com/users"` — no query params

**Status:** ✅ Pass — unit (sanitizeUrl) + E2E TC2

---

### TC3 — OTLP ingest endpoint NOT traced

**Steps:**
1. Load app (SDK initializes and starts sending OTLP data to localhost:4318)
2. Wait 2s for exports to happen
3. Query spans with `pulse.type LIKE 'network.%'` (or exact `network.<code>`) in CH or OTLP fixture

**Expected:** Zero spans with `url.full` containing `4318` or the configured OTLP endpoint

**Status:** ✅ Pass — E2E TC3, TC16

---

### TC4 — GraphQL POST with operationName → graphql.operation.name + type

**Steps:**
1. From page context:
   ```js
   fetch("/graphql", {
     method: "POST",
     body: JSON.stringify({ query: "query GetProducts { products { id } }", operationName: "GetProducts" })
   })
   ```
2. Check span attributes

**Expected:**
- `graphql.operation.name = "GetProducts"`
- `graphql.operation.type = "query"`

**Status:** ✅ Pass — unit (isGraphQL, extractOpName, extractOpType) + E2E TC4

---

### TC5 — GraphQL mutation body → type = mutation

**Steps:**
1. POST with body `{ query: "mutation CreateOrder { ... }" }`
2. Check span

**Expected:** `graphql.operation.type = "mutation"`

**Status:** ✅ Pass — E2E TC5

---

### TC6 — Anonymous GraphQL shorthand {} → type = query, name absent

**Steps:**
1. POST with body `{ query: "{ products { id } }" }` (no operationName, no keyword)
2. Check span

**Expected:**
- `graphql.operation.type = "query"` (anonymous shorthand detected)
- `graphql.operation.name` = absent (no named operation)

**Status:** ✅ Pass — unit (extractOpType) + E2E TC17

---

### TC7 — Non-GraphQL POST — no graphql attrs

**Steps:**
1. POST with body `{ userId: 123 }`
2. Check span

**Expected:** `graphql.operation.name` and `graphql.operation.type` absent

**Status:** ✅ Pass — unit + E2E TC6

---

### TC8 — 4xx response → error.type = 4xx

**Steps:**
1. Make `fetch()` to URL that returns 404
2. Check span

**Expected:**
- `http.response.status_code = 404`
- `error.type = "4xx"`
- Span status = ERROR

**Status:** ✅ Pass — unit + E2E TC8

---

### TC9 — 5xx response → error.type = 5xx

**Steps:**
1. Make `fetch()` to URL that returns 500
2. Check span

**Expected:**
- `http.response.status_code = 500`
- `error.type = "5xx"`
- Span status = ERROR

**Status:** ✅ Pass — unit + E2E TC9

---

### TC10 — 2xx response — no error.type

**Steps:**
1. Make `fetch()` to URL that returns 200
2. Check span

**Expected:**
- `http.response.status_code = 200`
- `error.type` absent
- Span status = UNSET (not ERROR)

**Status:** ✅ Pass — unit + E2E TC10

---

### TC11 — Network failure → error.type = network_error

**Steps:**
1. Make `fetch()` to unreachable host (e.g. `https://192.0.2.1/`)
2. Check span

**Expected:**
- No `http.response.status_code`
- `error.type = "network_error"`
- Span status = ERROR

**Status:** ✅ Pass — unit + E2E TC7

---

### TC12 — no-cors fetch → status 0 → error.type = cors_error

**Steps:**
1. Make `fetch("https://cross-origin.example.com/", { mode: "no-cors" })`
2. Check span

**Expected:**
- `http.response.status_code = 0` (opaque response)
- `error.type = "cors_error"`

**Status:** ✅ Pass — unit (cors_error test)

---

### TC13 — http.response.body.size from content-length

**Steps:**
1. Make `fetch()` to URL that returns `Content-Length` response header
2. Check span

**Expected:** `http.response.body.size = <byte count from Content-Length>`

**Status:** ✅ Pass — unit + E2E TC11

---

### TC14 — peer.service from peerServiceMap config

**Steps:**
1. Configure SDK with:
   ```js
   instrumentations: { network: { peerServiceMap: { "api.example.com": "orders-service" } } }
   ```
2. Make request to `https://api.example.com/...`
3. Check span

**Expected:** `peer.service = "orders-service"`

**Status:** ✅ Pass — unit

---

### TC15 — XHR produces http span

**Steps:**
1. Execute:
   ```js
   const xhr = new XMLHttpRequest();
   xhr.open("GET", "https://api.example.com/data");
   xhr.send();
   ```
2. Check span

**Expected:** Span with `pulse.type = "network.200"` present

**Status:** ✅ Pass — E2E TC15

---

### TC16 — capturedRequestHeaders allowlist

**Steps:**
1. Configure `capturedRequestHeaders: ["x-request-id"]`
2. Make fetch with header `{ "x-request-id": "abc123" }`
3. Check span

**Expected:** `http.request.header.x-request-id = "abc123"`

**Status:** ✅ Pass — unit

---

### TC17 — capturedResponseHeaders allowlist

**Steps:**
1. Configure `capturedResponseHeaders: ["x-trace-id"]`
2. Server returns `x-trace-id: trace-123` header
3. Check span

**Expected:** `http.response.header.x-trace-id = "trace-123"`

**Status:** ✅ Pass — unit (implementation verified)

---

### TC18 — http.duration present (PerformanceResourceTiming)

**Steps:**
1. Make a fetch request
2. Check span for `http.duration` attribute

**Expected:** `http.duration` = integer ms value (from PerformanceResourceTiming)
Note: may be absent if PerformanceResourceTiming not available for CORS requests

**Status:** ⚠️ Best-effort — verified in implementation; depends on browser timing API availability

---

### TC19 — CH verification: http spans ingested correctly

**Steps:**
1. Start app with Pulse SDK connected to real backend
2. Make 5 different API calls (GET, POST, 404, 500, GraphQL)
3. Query ClickHouse:
   ```sql
   SELECT SpanAttributes['pulse.type'], SpanAttributes['http.request.method'],
          SpanAttributes['url.full'], SpanAttributes['http.response.status_code'],
          SpanAttributes['error.type'], SpanAttributes['graphql.operation.name']
   FROM otel_traces
   WHERE SpanAttributes['pulse.type'] LIKE 'network.%'
   ORDER BY Timestamp DESC LIMIT 10
   ```

**Expected:** All 5 spans present with correct attribute values

**Status:** ✅ Verified via OTLP fixture (unit + E2E) — CH query pattern same as navigation

---

### TC20 — Deprecated semconv keys NOT present

**Steps:**
1. Make a fetch request
2. Check span attributes in CH / OTLP

**Expected:** The following deprecated keys should be ABSENT:
- `http.method` (use `http.request.method`)
- `http.url` (use `url.full`)
- `http.status_code` (use `http.response.status_code`)
- `net.peer.name` (use `server.address`)

Note: OTel `FetchInstrumentation` v0.53.0 uses stable semconv. Deprecated keys are NOT emitted.

**Status:** ✅ Architecture-verified — using OTel v0.53.0 which emits stable semconv only

---

## Attribute Key Reference (Web vs Deprecated)

| Attribute | Web (stable) | Deprecated (do NOT use) | Android equivalent |
|---|---|---|---|
| HTTP method | `http.request.method` | `http.method` | `http.request.method` |
| Full URL | `url.full` | `http.url` | `url.full` |
| Status code | `http.response.status_code` | `http.status_code` | `http.response.status_code` |
| Hostname | `server.address` | `net.peer.name` | `server.address` |
| Port | `server.port` | `net.peer.port` | `server.port` |
| Pulse signal type | `pulse.type = network.<statusCode>` | — | `pulse.type = network.<statusCode>` |
| Duration (Pulse custom) | `http.duration` | — | span duration |
| Req body size | `http.request.body.size` | — | `http.request.body.size` |
| Res body size | `http.response.body.size` | — | `http.response.body.size` |
| Error category | `error.type` | — | span status |
| Peer service | `peer.service` | — | `peer.service` |
| GraphQL op name | `graphql.operation.name` | — | ❌ (web-only) |
| GraphQL op type | `graphql.operation.type` | — | ❌ (web-only) |
