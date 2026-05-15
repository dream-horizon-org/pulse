# Network Instrumentation — SPEC

Package: `@dreamhorizonorg/pulse-web`  
Files: `src/instrumentations/network.ts`, `src/utils/network-http.ts`

---

## 1. What this does (plain English)

Every time your app makes an HTTP request — either via `fetch()` or `XMLHttpRequest` — this instrumentation captures it as an OTel **client span** and stamps Pulse-specific metadata onto it (status code, duration, error type, etc.).

The data flows like this:

```
App makes fetch() / XHR
  → OTel FetchInstrumentation / XMLHttpRequestInstrumentation hooks in
  → our callback (applyCustomAttributesOnSpan) stamps Pulse attributes
  → span sent to OTLP collector
  → lands in ClickHouse as a network span
```

The dashboard then groups these spans by `pulse.type` (e.g. `network.200`, `network.404`) for the Network tab.

---

## 2. Install and uninstall lifecycle

`NetworkInstrumentation.install(sdk)` runs once. It:

1. Does nothing (no-op) if `window` is undefined (SSR / Node environment) or if already active.
2. Does nothing if no `tracerProvider` is set on the SDK context.
3. Creates one `FetchInstrumentation` and one `XMLHttpRequestInstrumentation` — both from the upstream OTel JS packages.
4. Hands the `tracerProvider` to both and calls `.enable()` on each.

`NetworkInstrumentation.uninstall()` calls `.disable()` on both instrumentations and clears the references. It is safe to call twice — the second call is a no-op.

---

## 3. What gets ignored (URL filter)

`buildNetworkIgnoreUrls(endpointBaseUrl, blockedUrls)` builds a list of URL patterns that are **never** turned into spans:


| Pattern                                                             | Why                                                                  |
| ------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Anything starting with the OTLP `endpointBaseUrl`                   | Prevents the SDK from tracing its own export calls (feedback loop)   |
| Pulse REST API at `:8080` (when OTLP is on `:4318`, local dev only) | Same reason — config fetch + interaction config are on the same host |
| User-supplied `instrumentations.network.blockedUrls` entries        | App-specific URLs to exclude (e.g. analytics pings)                  |


This list is passed into both `FetchInstrumentation` and `XMLHttpRequestInstrumentation` as `ignoreUrls`.

---

## 4. Config options

All options live under `sdkConfig.instrumentations.network`:


| Option                         | Type                                    | Default | What it does                                                                                                                                                                                                                      |
| ------------------------------ | --------------------------------------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `captureQueryParams`           | boolean                                 | `false` | When `false`, strips the entire query string from `url.full`. When `true`, keeps query params but redacts sensitive ones (see §7).                                                                                                |
| `blockedUrls`                  | `(string | RegExp)[]`                   | `[]`    | Extra URLs to never trace (merged into the ignore list).                                                                                                                                                                          |
| `capturedRequestHeaders`       | `string[]`                              | none    | Request headers to copy as `http.request.header.<name>` on spans. **Fetch only** — XHR cannot access sent request headers (browser API limitation). Sensitive header names are always blocked regardless of this config (see §7). |
| `capturedResponseHeaders`      | `string[]`                              | none    | Response headers to copy as `http.response.header.<name>` on spans. Works for both Fetch and XHR.                                                                                                                                 |
| `peerServiceMap`               | `Record<string, string>`                | none    | Map of hostname → logical service name. Sets `peer.service` on spans. Example: `{ "api.example.com": "catalogue-service" }`.                                                                                                      |
| `propagateTraceHeaderCorsUrls` | `string | RegExp | (string | RegExp)[]` | none    | Hosts that should receive W3C `traceparent` / `tracestate` headers on outgoing requests. Passed directly to the OTel instrumentations.                                                                                            |


---

## 5. Attributes set on every span

### 5.1 Always set


| Attribute                   | Value                                                                                  | Example                                                |
| --------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| `pulse.type`                | `network.<statusCode>` — the core ClickHouse grouping key                              | `network.200`, `network.404`, `network.0`              |
| `http.request.method`       | HTTP verb, always uppercased                                                           | `GET`, `POST`                                          |
| `url.full`                  | Sanitized URL (credentials stripped; query stripped or redacted per config)            | `https://api.example.com/products`                     |
| `server.address`            | Hostname from the URL                                                                  | `api.example.com`                                      |
| `server.port`               | Port number. Inferred as `443` for `https:` and `80` for `http:` when no explicit port | `443`                                                  |
| `platform`                  | `web`                                                                                  | set via Resource by SDK core, not this instrumentation |
| `session.id`, `screen.name` | Set by global span processors, not this instrumentation                                | —                                                      |


### 5.2 Set when available


| Attribute                     | Condition                                                                        | Value                                                                                                              |
| ----------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `http.response.status_code`   | When the response status is known                                                | `200`, `404`, `0`                                                                                                  |
| `http.duration`               | When the browser's `PerformanceResourceTiming` API has an entry for this URL     | Duration in ms (integer). **Pulse-custom attribute** — not the same as OTel `http.client.request.duration` metric. |
| `network.protocol.version`    | When `PerformanceResourceTiming.nextHopProtocol` is available                    | `1.1`, `2`, `3`                                                                                                    |
| `http.request.body.size`      | When `content-length` request header is accessible (Fetch only)                  | Integer bytes                                                                                                      |
| `http.response.body.size`     | When `content-length` response header is present                                 | Integer bytes                                                                                                      |
| `peer.service`                | When the request hostname matches an entry in `peerServiceMap`                   | `catalogue-service`                                                                                                |
| `http.request.header.<name>`  | When `capturedRequestHeaders` is configured (Fetch only)                         | Header value                                                                                                       |
| `http.response.header.<name>` | When `capturedResponseHeaders` is configured                                     | Header value                                                                                                       |
| `graphql.operation.name`      | When `graphqlRequestBody` is passed in (not wired from Fetch/XHR yet — see §6.3) | `GetProducts`                                                                                                      |
| `graphql.operation.type`      | Same condition                                                                   | `query`, `mutation`, `subscription`                                                                                |


### 5.3 Error classification — `error.type`


| Status                            | Span status | `error.type` set |
| --------------------------------- | ----------- | ---------------- |
| Empty / unparseable URL           | ERROR       | `network_error`  |
| Status unknown (`undefined`)      | ERROR       | `network_error`  |
| Status `0` (CORS opaque response) | ERROR       | `cors_error`     |
| Status `400–499`                  | ERROR       | `4xx`            |
| Status `500+`                     | ERROR       | `5xx`            |
| Status `1xx–3xx`                  | OK          | not set          |


`pulse.type` is always set regardless — even `network_error` cases get `network.0`.

---

## 6. How Fetch and XHR differ internally

### 6.1 Fetch

The callback receives a `Request` (or `RequestInit`) and a `Response`. URL is resolved by priority:

1. `Response.url` (post-redirect final URL — preferred)
2. `Request.url`
3. URL already on the span (set by OTel upstream)

Method comes from the span attribute first, then falls back to `Request.method` / `RequestInit.method`, defaulting to `GET`.

Request headers are accessible via `Request.headers.get()` or from the `RequestInit.headers` object/array.

### 6.2 XHR

The callback receives the raw `XMLHttpRequest` object. The guard `xhr.readyState !== DONE` ensures the callback only runs when the request is fully complete. URL comes from `xhr.responseURL`. Method falls back to parsing the OTel span name (e.g. `"HTTP GET"` → `"GET"`).

**Request headers are not accessible on XHR** — the browser does not expose sent headers after dispatch. `capturedRequestHeaders` is silently ignored for XHR spans.

### 6.3 GraphQL body (not yet wired)

`applyPulseHttpClientSpanAttributes` has a `graphqlRequestBody` parameter that, when provided, calls `extractGraphQlMeta` to parse `operationName` and `operationType` from the JSON body. However, `NetworkInstrumentation` currently **does not pass this** — Fetch body is async and cannot be read synchronously in the callback. This is a known deferred feature.

`extractGraphQlMeta` handles: named queries, mutations, subscriptions, anonymous operations (falls back to `operationName` JSON field), and ignores bodies over 262,144 bytes or invalid JSON.

---

## 7. Privacy and sensitive data

### 7.1 URL query params

By default (`captureQueryParams: false`), the **entire query string is stripped** from `url.full` before storing on the span.

When `captureQueryParams: true`, query params are kept but the following param **names** have their values replaced with `***`:

`token`, `access_token`, `refresh_token`, `id_token`, `bearer`, `api_key`, `apikey`, `password`, `secret`, `client_secret`, `signature`, `sig`, `auth`

URL credentials (`user:password@host`) are **always stripped** unconditionally.

### 7.2 Captured headers denylist

Even if a header name appears in `capturedRequestHeaders` or `capturedResponseHeaders`, the following are **always blocked** and never written to spans:

`authorization`, `cookie`, `set-cookie`, `proxy-authorization`, `x-api-key`, `x-auth-token`

---

## 8. Web ↔ Android divergences


| #   | What                      | Web                                                              | Android                                                                                                                                                       |
| --- | ------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | `error.type`              | Set: `network_error`, `cors_error`, `4xx`, `5xx`                 | **Not set** — Android only sets `pulse.type`. Dashboard filters on `error.type` are web-only.                                                                 |
| D2  | URL path normalization    | **Not done** — raw paths with UUIDs/IDs go into `url.full` as-is | `PulseNetworkingUtils.redactUrl` normalizes UUID, numeric IDs ≥ 3 digits, ULIDs, git hashes → `[redacted]`. High ClickHouse cardinality risk on web at scale. |
| D3  | Request header capture    | Fetch only (browser limitation)                                  | OkHttp interceptor has full request + response header access                                                                                                  |
| D4  | Non-standard HTTP methods | ✅ **Fixed** — unknown methods map to `http.request.method = "_OTHER"` + `http.request.method_original = <original>` via `KNOWN_HTTP_METHODS` check in `network-http.ts` | OkHttp maps to `_OTHER` via upstream OTel `HttpConstants.KNOWN_METHODS`. Both platforms now compliant. |


---

## 9. Test coverage

### 9.1 Unit tests

`src/__tests__/network-instrumentation.test.ts`:

- Install/uninstall lifecycle
- SSR no-op (`window` undefined)
- Ignore URL builder (collector + custom patterns)
- Fetch callback: `pulse.type`, method, status, `peer.service`, header capture, `propagateTraceHeaderCorsUrls` forwarding
- Double-uninstall is idempotent
- XHR `applyCustomAttributesOnSpan` stamps `pulse.type` + method at `readyState DONE`
- XHR `applyCustomAttributesOnSpan` returns early when `readyState < DONE`
- `uninstall()` calls `disable()` on both Fetch and XHR instrumentations

`src/__tests__/network-http.test.ts`:

- `applyPulseHttpClientSpanAttributes`: method, status codes, `pulse.type` pattern, CORS error, `4xx`/`5xx` error types, URL sanitization, request/response body size, non-standard method → `_OTHER` + `http.request.method_original`
- `sanitizeHttpUrl`: query stripping, sensitive param redaction, credential stripping
- `buildNetworkIgnoreUrls`: OTLP prefix, local dev REST port, custom blocked URLs — **tests in `network-http.test.ts`**
- `extractGraphQlMeta`: named query, mutation, subscription, anonymous op, overflow, invalid JSON

### 9.2 E2E (Playwright, `@M4 network e2e`)

`examples/ecommerce-demo/e2e/m4-network.spec.ts`:

- P1–P5: GET 200, contract attribute assertions, OTLP URL exclusion
- G1: gate off → no spans
- E1–E5: error taxonomy (CORS, 404, abort, timeout)
- E2: local disable
- C1: consent off → no spans
- ISS-N06: `captureQueryParams: true` keeps params, redacts sensitive values
- ISS-N07: `blockedUrls` config suppresses spans for matched URL
- ISS-N08: `peerServiceMap` sets `peer.service` attribute
- ISS-N09: `propagateTraceHeaderCorsUrls` injects W3C `traceparent` header

`examples/nextjs-demo/e2e/nextjs-demo.spec.ts`:
- `@M4 network — Next.js demo`: ISS-N06–N09 mirrored for Next.js App Router + Pages Router

### 9.3 Known test gaps (tracked in `REVIEW_Errors-Web-vitals-Network.md`)


| ID          | What is missing                                                                                |
| ----------- | ---------------------------------------------------------------------------------------------- |
| ~~ISS-N03~~ | ~~No unit test for XHR `applyCustomAttributesOnSpan` callback~~ — **fixed** in `network-instrumentation.test.ts` |
| ~~ISS-N04~~ | ~~No unit test for `http.response.body.size` from `Content-Length`~~ — **fixed** in `network-http.test.ts` |
| ~~ISS-N05~~ | ~~`extractGraphQlMeta` missing cases~~ — **fixed**: mutation, subscription, anonymous, overflow, invalid JSON all covered in `network-http.test.ts` |
| ~~ISS-N06~~ | ~~No E2E for `captureQueryParams: true`~~ — **fixed** in `m4-network.spec.ts` (ISS-N06) |
| ~~ISS-N07~~ | ~~No E2E for `blockedUrls` config~~ — **fixed** in `m4-network.spec.ts` (ISS-N07) |
| ~~ISS-N08~~ | ~~No E2E for `peerServiceMap` → `peer.service`~~ — **fixed** in `m4-network.spec.ts` (ISS-N08) |
| ~~ISS-N09~~ | ~~No E2E for `propagateTraceHeaderCorsUrls`~~ — **fixed** in `m4-network.spec.ts` (ISS-N09); Playwright route intercept asserts `traceparent` header present |
| ~~ISS-N10~~ | ~~No test that uninstall disables instrumentations~~ — **fixed** in `network-instrumentation.test.ts`; asserts `disable()` called once on both Fetch + XHR |
| ~~ISS-N11~~ | ~~No test for `readyState < DONE` guard on XHR~~ — **fixed** in `network-instrumentation.test.ts` |
| ~~ISS-N14~~ | ~~No unit test for non-standard method → `_OTHER` + `http.request.method_original`~~ — **fixed**: `PURGE` case added in `network-http.test.ts` |


---

## 10. Known bugs and deferred work


| ID      | Area            | Summary                                                                                                    |
| ------- | --------------- | ---------------------------------------------------------------------------------------------------------- |
| ISS-N02 | Bug             | `capturedRequestHeaders` silently no-ops for XHR — no warning emitted                                    |
| ISS-N12 | Decision needed | URL path segment normalization for ClickHouse cardinality (Android has it; web does not)                  |


---

## 11. Open questions

1. Should `pulse.type` eventually unify to a single `http` token plus attributes (breaking change)?
2. Should fetch request body size be estimated when `Request` has a readable stream?
3. Should `urlTemplateRules` (path-segment normalization for IDs/UUIDs) be added to control `url.full` cardinality in ClickHouse? (D2 — Android `PulseNetworkingUtils.redactUrl` handles this; web does not.)
4. Should `error.type` be added to Android network spans for cross-platform dashboard parity? (D1)
