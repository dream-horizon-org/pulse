# PLAN-B — Network HTTP client spans

**OTel semconv alignment:** see [PLAN-C-otel-spec-alignment.md](./PLAN-C-otel-spec-alignment.md) (intentional `error.type` deviation; `url.full` / `server.port` / `network.protocol.version`).

## Canonical detail table

Full attribute and parity table: **[`../v1/02-instrumentations/network.md`](../v1/02-instrumentations/network.md)** (source of truth).

## Lifecycle

1. `PulseWebSDK.finishStart` builds trace pipeline → assigns `_webTracerProvider`.
2. `bindGlobalProviders` registers global tracer.
3. `InstrumentationRegistry.installAll` → `NetworkInstrumentation.install` → `setTracerProvider` + `enable()` on Fetch + XHR instrumentations.
4. Export: batch span processor; **`pagehide`** forces trace flush (with logs/metrics).

## Privacy

- **`url.full`:** query string stripped unless `instrumentations.network.captureQueryParams === true`.
- **OTLP:** URLs under `endpointBaseUrl` prefix excluded via `ignoreUrls` regex.

## Unit matrix (Vitest)

| Case | Expect |
|------|--------|
| `sanitizeHttpUrl` + strip query | No `?` in output when `captureQueryParams` false. |
| `extractGraphQlMeta` | Named operation / type from JSON body string. |
| `buildNetworkIgnoreUrls` | Prefix regex matches OTLP host base URL. |
| `methodFromOtelClientSpanName` | `HTTP GET` → `GET`; plain `POST` → `POST`; unknown → `GET`. |
| `networkPulseType` | `200` → `network.200`; `undefined` / NaN → `network.0`. |
| `applyPulseHttpClientSpanAttributes` — errors | 404 / 500 → `error.type` `4xx`/`5xx`; missing status → `network_error`; status `0` → `cors_error`; matching `pulse.type`. |

## E2E outline (Playwright)

| ID | Scenario | Spec |
|----|----------|------|
| P1 / P2 / P4 | **`fetch`** stubbed same-origin → `pulse.type === network.<code>` (e.g. `network.200`), finite `http.response.status_code`, finite **`server.port`**, `url.full` without `?` / secret, `session.id` + `screen.name` truthy, stable HTTP attrs. **P4:** if `http.duration` present, assert finite number. **`network.protocol.version`:** not asserted (Playwright route — see deferrals). | `m4-network` “P1/P2/P4” |
| P3 | **`XMLHttpRequest`** `open`+`send` to stubbed path → same contract; `url.full` strips query. | `m4-network` “P3” |
| P5 | After init + export activity, **no** `network.*` client span whose `url.full` matches OTLP export paths (`/v1/traces`, `/v1/logs`, `/v1/metrics`). | `m4-network` “P5” |
| G1 | **Remote gate off** — seed `network_instrumentation` `sessionSampleRate: 0` for `pulse_web_js` → `session.start`, **`otlp.reset()`**, `fetch` → **zero** matching client spans (`findAllNetworkSpans`). | `m4-network` “G1” |
| E1 | Stub **404** / **500** → `pulse.type` `network.404` / `network.500`, `error.type` `4xx` / `5xx`, truthy `session.id` + `screen.name`. | `m4-network` “E1” (two tests) |
| E2 | **Local** `instrumentations.network.enabled: false` — demo query `?pulse_network_enabled=0` (see `App.tsx`) while remote gate default → probe `fetch` → **zero** network client spans. | `m4-network` “E2” |
| C1 | **`pulse_consent=denied`** — SDK does not start → zero network client spans (global consent; not network-specific). | `m4-network` “C1” |
| F1 | Batch window: `VITE_PULSE_BATCH_DELAY_MS=200` + `waitForTimeout` + `expect.poll` for probe span. | implicit in `m4-network` |

## Explicit deferrals

| Item | Rationale |
|------|-----------|
| **`network.protocol.version` in Playwright E2E** | **`page.route` fulfillment is not a real network stack** — `PerformanceResourceTiming.nextHopProtocol` is usually absent, so the attribute is correctly omitted. Covered by Vitest (`resourceTimingProtocolVersion`). |
| **`http.duration` in Playwright E2E** | Often **absent** or zero when responses come from `page.route` fulfillment (no real `PerformanceResourceTiming`). Spec asserts finite number **only when** `http.duration` is present (`m4-network` P1/P2/P4). |
| **`network_change` feature** | Same as ADR: no runtime listener; resource-only hints at init. |
| GraphQL from live `fetch` body | Sync hook cannot `await` `request.clone().text()` before span end — future async extraction or demo-only POST with body logged separately. |
| GraphQL from XHR | Request payload not introspectable after send via standard APIs. |
