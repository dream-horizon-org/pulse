# Phase 0 — Research: OTel JS browser + Pulse SDK wiring

## Where it plugs in

| Layer | Role |
|-------|------|
| `InstrumentationRegistry.installAll()` | `registerAndInstall(new NetworkInstrumentation(), InstrumentationKeys.NETWORK)` after providers exist. |
| `SdkContext.tracerProvider` | **`WebTracerProvider`** from `createProviders` — `FetchInstrumentation` / `XMLHttpRequestInstrumentation` call `setTracerProvider(provider)` + `enable()`. Public getter on `PulseWebSDK` mirrors `loggerProvider`. |
| `PulseGlobalAttributesProcessor` | Implements `SpanProcessor.onStart` — stamps `session.id`, `screen.name`, etc. on **every** span including HTTP client spans. |
| `FeatureGate` | `InstrumentationKeys.NETWORK` → `PulseFeature.NETWORK_INSTRUMENTATION`. |
| Ignore OTLP | `buildNetworkIgnoreUrls(endpointBaseUrl)` → regex prefix so Pulse’s own `/v1/traces|logs|metrics` requests are not traced. |

## Implementation notes

- **SSR:** `NetworkInstrumentation.install` returns immediately when `typeof window === "undefined"`.
- **Uninstall:** OTel instrumentations `disable()` unpatch fetch/XHR when registry `uninstallAll()` runs.
- **Deprecated OTel attrs:** Base instrumentation may still emit legacy keys (`http.url`, …); Pulse adds **stable** semconv duplicates per [`network.md`](../v1/02-instrumentations/network.md).

## Deferred (documented in PLAN-B)

- **GraphQL from `fetch` Request body:** requires async read — not applied in sync `applyCustomAttributesOnSpan`; optional future hook.
- **XHR GraphQL:** request body not readable post-send via standard API — deferred.
