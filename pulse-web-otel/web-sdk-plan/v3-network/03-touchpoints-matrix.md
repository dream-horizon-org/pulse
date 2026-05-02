# Phase 1 — Touchpoints matrix (network / HTTP client spans)

| Area | File | Change |
|------|------|--------|
| Semconv | [`src/semconv.ts`](../../src/semconv.ts) | HTTP keys + `network.protocol.version`; `pulse.type` via [`networkPulseType`](../../src/utils/network-http.ts). |
| Config | [`src/types/config.ts`](../../src/types/config.ts) | `InstrumentationConfig.network` subtree; `emitRequestDurationMetric` reserved (PLAN-C P3, not wired). |
| SdkContext | [`src/types/instrumentation-registry.ts`](../../src/types/instrumentation-registry.ts) | `tracerProvider?: WebTracerProvider`. |
| SDK lifecycle | [`src/sdk.ts`](../../src/sdk.ts) | `_webTracerProvider` + getter `tracerProvider`; flush unchanged. |
| Helpers | [`src/utils/network-http.ts`](../../src/utils/network-http.ts) | Sanitize URL (incl. credentials), ignore list, `applyPulseHttpClientSpanAttributes`, `resourceTimingProtocolVersion` (PLAN-C). |
| Instrumentation | [`src/instrumentations/network.ts`](../../src/instrumentations/network.ts) | Fetch + XHR OTel instrumentations, Pulse hooks. |
| Registry | [`src/instrumentation-registry.ts`](../../src/instrumentation-registry.ts) | `registerAndInstall(..., InstrumentationKeys.NETWORK)`. |
| Remote gate | [`src/types/remote-config.ts`](../../src/types/remote-config.ts) | No change — `NETWORK_INSTRUMENTATION` already defined. |
| Backend | `Features.java` / `DefaultSdkConfigTemplate*` | **No change** — enum row already exists for `network_instrumentation`. |
| Unit tests | [`src/__tests__/network-http.test.ts`](../../src/__tests__/network-http.test.ts) | Pure helpers + attr contract. |
| E2E | [`examples/ecommerce-demo/e2e/m4-network.spec.ts`](../../examples/ecommerce-demo/e2e/m4-network.spec.ts) | Positive `network.*` span + gate-off zero spans. |
| OTel alignment | [`PLAN-C-otel-spec-alignment.md`](./PLAN-C-otel-spec-alignment.md) | Docs + implementation tracker (credentials, `server.port`, protocol version). |
| Demo gates | [`examples/ecommerce-demo/package.json`](../../examples/ecommerce-demo/package.json) | Append spec to `e2e:web-sdk-gates`. |
| Runtime log | [`web-sdk-plan/agent-runtime/test-run-log.md`](../agent-runtime/test-run-log.md) | Gate command + result. |
