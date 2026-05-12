# instrumentations/network

## 1. Purpose

Wrap `fetch` and `XMLHttpRequest` to emit OTel `CLIENT` spans with HTTP semconv attributes, then tag them with Pulse `pulse.type = network.<statusCode>` to match Android `HttpInstrumentation`.

## 2. Source location

- `pulse-web-otel/src/instrumentations/network.ts` — `NetworkInstrumentation`
- `pulse-web-otel/src/utils/network-http.ts` — span attribute helpers, ignore-URL builders, method/status resolution

## 3. Public surface

```ts
class NetworkInstrumentation implements PulseInstrumentation {
  readonly name = "network";
  install(sdk: SdkContext): void;
  uninstall(): void;
}
```

Gated by `PulseFeature.NETWORK_INSTRUMENTATION`. Config knobs (`instrumentations.network`): `ignoreUrls`, `propagateTraceHeader`, `captureRequestBodySize`, `captureResponseBodySize` (see `NetworkSpanOptionalConfig`).

## 4. Internal design

- Creates two upstream OTel instrumentations:
  - `FetchInstrumentation` from `@opentelemetry/instrumentation-fetch`
  - `XMLHttpRequestInstrumentation` from `@opentelemetry/instrumentation-xml-http-request`
- Wires `applyCustomAttributesOnSpan` to call `applyPulseHttpClientSpanAttributes`, which:
  - Sets `pulse.type = network.<status>` (or `network.error` if no response).
  - Adds `http.request.method`, `url.full`, `http.response.status_code`, body sizes, `server.address`, `server.port`, `network.protocol.version` (from Resource Timing `nextHopProtocol` when available).
- `disableInstrumentationBestEffort` swallows upstream `disable()` errors during `uninstall`.
- `instrumentsActive` flag guards against double-disable noise.
- The collector default endpoint and Pulse's own export URL are auto-added to `ignoreUrls` to prevent self-instrumentation loops (`buildNetworkIgnoreUrls`).

## 5. Dependencies

- `@opentelemetry/instrumentation-fetch`, `@opentelemetry/instrumentation-xml-http-request`
- `@opentelemetry/api` (trace, context)

## 6. Data contracts

`pulse.type = network.<statusCode>` (e.g. `network.200`, `network.404`, `network.error`). Attribute keys (`PulseWebSemconv.AttributeKey`): `http.request.method`, `url.full`, `http.response.status_code`, `http.request.body.size`, `http.response.body.size`, `server.address`, `server.port`, `network.protocol.version`. Span kind `CLIENT`; span name from upstream OTel (e.g. `HTTP GET`).

## 7. Tests

- `src/__tests__/m1.test.ts`
- E2E: `examples/ecommerce-demo/e2e/m4-network.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/network/SPEC.md`. The `pulse.type = network.<status>` schema (vs a single `http`) is Android parity for the ClickHouse `PulseType` materialised column.

## 9. Rebuild recipe

1. Wrap upstream Fetch and XHR instrumentations.
2. Install `applyCustomAttributesOnSpan` hooks that call `applyPulseHttpClientSpanAttributes`.
3. Auto-extend `ignoreUrls` with the SDK's own export URL.
4. Guard `disable()` with best-effort try/catch on `uninstall`.
