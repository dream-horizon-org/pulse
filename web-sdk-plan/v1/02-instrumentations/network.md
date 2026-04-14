# 02.2 — Network Instrumentation

**Goal:** Capture every outbound HTTP request (Fetch and XHR) as a span with timing, status code, payload size, and GraphQL operation name — without any app code changes.

**File:** `src/instrumentations/network.ts`
**Android equivalent:** `OkHttp3Instrumentation`, `HttpUrlConnectionInstrumentation`

---

## Signals Produced

### `pulse.type: http` — one span per HTTP request

| Attribute | Type | Source | Required |
|---|---|---|---|
| `pulse.type` | string | `"http"` | ✅ |
| `http.method` | string | Request method (`GET`, `POST`, etc.) | ✅ |
| `http.url` | string | Sanitised request URL | ✅ |
| `http.status_code` | long | Response HTTP status | ✅ |
| `http.request_content_length` | long | `Content-Length` request header (bytes) | optional |
| `http.response_content_length` | long | `Content-Length` response header (bytes) | optional |
| `net.peer.name` | string | Hostname extracted from URL | ✅ |
| `http.duration` | long | Total request duration (ms) | ✅ |
| `graphql.operation.name` | string | Parsed from request body | optional |
| `graphql.operation.type` | string | `"query"` / `"mutation"` / `"subscription"` | optional |
| Custom request headers | string | Configurable allowlist | optional |
| Custom response headers | string | Configurable allowlist | optional |

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

        // GraphQL
        const body = getRequestBody(request);
        if (isGraphQL(body)) {
          span.setAttribute('graphql.operation.name', extractOpName(body) ?? '');
          span.setAttribute('graphql.operation.type', extractOpType(body) ?? '');
        }

        // Payload sizes
        const reqLen = getHeader(request, 'content-length');
        if (reqLen) span.setAttribute('http.request_content_length', Number(reqLen));
        const resLen = getResponseHeader(response, 'content-length');
        if (resLen) span.setAttribute('http.response_content_length', Number(resLen));

        // Custom headers (allowlist)
        config.capturedRequestHeaders?.forEach(h => {
          const v = getHeader(request, h);
          if (v) span.setAttribute(`http.request.header.${h}`, v);
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
  expect(span['http.method']).toBe('GET');
  expect(span['http.url']).toBe('https://api.example.com/data');
  expect(span['http.status_code']).toBe(200);
});

test('OTLP calls are not traced', async ({ page }) => {
  await page.goto('/test-page');
  const spans = receiver.spans.filter(s => s['http.url']?.includes('/v1/traces'));
  expect(spans).toHaveLength(0);
});
```

---

## Done Criteria

- [ ] Every `fetch()` call produces an `http` span with `http.method`, `http.url`, `http.status_code`
- [ ] XHR calls also produce `http` spans
- [ ] Pulse's own OTLP endpoints excluded from tracing
- [ ] GraphQL `operation.name` and `operation.type` extracted from POST body
- [ ] Query params stripped from URL by default
- [ ] `http.request_content_length` / `http.response_content_length` present when `Content-Length` header available
- [ ] All unit tests passing
