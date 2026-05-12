# RN · Network

XHR/fetch interceptor → OTel client span with `pulse.type = http`.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [../core/semconv](../core/semconv.md), [../core/facade](../core/facade.md).

## Source location

- `pulse-react-native-otel/src/network-interceptor/` (module with index + helpers)
- `pulse-react-native-otel/src/redaction.ts` — header/body redaction.

## Public surface

Auto-installed at `Pulse.init` when `config.enableNetwork !== false`. No per-call API.

## Internal design

1. Monkey-patch global `XMLHttpRequest.prototype.open/send` (RN's `fetch` is built on XHR so both are covered).
2. On `open` → start span with `http.request.method`, `url.full`.
3. On `onreadystatechange`/`onload` → set `http.response.status_code`, `http.response.body.size` (from `Content-Length` header), end span.
4. Redaction pass in `redaction.ts` strips `Authorization` / `Cookie` / PII query params.
5. Skip URLs that match the Collector endpoint to avoid self-reporting loops.

## Data contracts

- `pulse.type = http`
- `platform = react-native`
- `http.request.method`, `url.full`, `server.address`, `http.response.status_code`, `http.response.body.size`
- `error.type` when `status >= 400` or network failure

## Tests

`src/__tests__/network-interceptor.test.ts` stubs XHR lifecycle and asserts span shape + redaction + self-report skip.

## History / decisions

XHR-level interception (vs. fetch wrapper) so apps using third-party HTTP libs built on XHR are covered for free. Self-report skip implemented by URL prefix match to the configured endpoint.

## Rebuild recipe

1. Keep references to the original `XMLHttpRequest.prototype.{open,send}`.
2. Patch with a wrapper that spans the lifecycle.
3. Add redaction defaults + a `shouldIgnoreUrl(url)` helper.
4. Ensure idempotency (don't re-patch if already patched).
