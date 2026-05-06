# 02.2 — Network Instrumentation

**Goal:** Capture every outbound HTTP request (Fetch and XHR) as a span with timing, status code, payload size, and GraphQL operation name — without any app code changes.

**File:** `src/instrumentations/network.ts`
**Android equivalent:** `OkHttp3Instrumentation`, `HttpUrlConnectionInstrumentation`

---

## Signals Produced

### `pulse.type: http` — one span per HTTP request

> **OTel alignment:** All attribute names follow the [stable OTel HTTP semconv](https://opentelemetry.io/docs/specs/semconv/http/http-spans/) (`http.request.method`, `url.full`, `http.response.status_code`, `server.address`). Deprecated names (`http.method`, `http.url`, `http.status_code`, `net.peer.name`) are NOT used.

| Attribute | Type | Source | Required |
|---|---|---|---|
| `pulse.type` | string | `"http"` | ✅ |
| `http.request.method` | string | Request method (`GET`, `POST`, etc.) | ✅ |
| `http.request.method_original` | string | Original method when `http.request.method = "_OTHER"` | conditional |
| `url.full` | string | Sanitised request URL | ✅ |
| `http.response.status_code` | long | Response HTTP status | ✅ |
| `http.request.body.size` | long | `Content-Length` request header (bytes) | optional |
| `http.response.body.size` | long | `Content-Length` response header (bytes) | optional |
| `server.address` | string | Hostname extracted from URL | ✅ |
| `server.port` | long | Port extracted from URL | optional |
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
        span.setAttribute('pulse.type', 'http');
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
        span.setAttribute('pulse.type', 'http');
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
  const span = await waitForSpan(receiver, 'http');
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

- [ ] Every `fetch()` call produces an `http` span with `http.request.method`, `url.full`, `http.response.status_code`, `server.address`
- [ ] XHR calls also produce `http` spans
- [ ] Pulse's own OTLP endpoints excluded from tracing
- [ ] GraphQL `operation.name` and `operation.type` extracted from POST body
- [ ] Query params stripped from URL by default
- [ ] `http.request.body.size` / `http.response.body.size` present when `Content-Length` header available
- [ ] `peer.service` set when `peerServiceMap` configured and hostname matches
- [ ] `http.request.method = "_OTHER"` + `http.request.method_original` set for non-standard HTTP methods
- [ ] 4xx/5xx responses set span status `ERROR`
- [ ] Network failures set span status `ERROR` with `error.type = "network_error"`
- [ ] All unit tests passing
