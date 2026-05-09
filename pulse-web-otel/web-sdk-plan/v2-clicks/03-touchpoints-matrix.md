# Touchpoints — click + rage (v2-clicks)

| Layer | Files |
|--------|--------|
| Semconv | `src/semconv.ts` (`PulseWebSemconv` — already has click attrs) |
| Config | `src/types/config.ts` — `instrumentations.clicks.rage` |
| Instrumentation | `src/instrumentations/clicks.ts`, **new** `src/instrumentations/click-rage-buffer.ts` |
| Target helpers | `src/instrumentations/click-target.ts` |
| Registry | `src/instrumentation-registry.ts` |
| Remote / gate | `src/remote-config.ts` (`PulseFeature.CLICK`), `src/feature-gate.ts` |
| SdkContext | `src/types/instrumentation-registry.ts`, `src/sdk.ts` (`loggerProvider` already) |
| Unit tests | `src/__tests__/click-rage-buffer.test.ts`, `src/__tests__/clicks-instrumentation.test.ts` |
| E2E | `examples/ecommerce-demo/e2e/m3-clicks.spec.ts`, `examples/ecommerce-demo/e2e/test-sdk-config.ts`, `examples/ecommerce-demo/package.json` (`e2e:web-sdk-gates`) |
| Log | `web-sdk-plan/agent-runtime/test-run-log.md` |

**Backend:** no new `Features` row (still `click`); rage tuning is **client config** only unless product later adds remote keys.
