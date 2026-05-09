# Phase 1 — Touchpoints: screen navigation signals (v4-screen-signals)

| Layer | Files | Details |
|---|---|---|
| **Semconv & constants** | `src/semconv.ts` | `SCREEN_LOAD`, `SCREEN_SESSION` on web (`tti` attr on load); RN keeps separate `screen_interactive` span semantics |
| | `src/instrumentations/navigation.ts` | **New** — navigation instrumentation class (page load + SPA route tracking + signal emission) |
| **Config & types** | `src/types/config.ts` | Add `PulseWebConfig.instrumentation.screenNavigation?: { routePatterns?: RoutePattern[] }` |
| | `src/types/instrumentation-registry.ts` | Extend `SdkContext` to include `loggerProvider?: LoggerProvider` |
| **Registry & wiring** | `src/instrumentation-registry.ts` | Register `NavigationInstrumentation` in `InstrumentationRegistry` |
| | `src/sdk.ts` | Instantiate `NavigationInstrumentation` in SDK init; pass `loggerProvider` to context |
| **Feature gate** | `src/remote-config.ts` | Add `PulseFeature.SCREEN_NAVIGATION` enum + descriptor |
| | `src/feature-gate.ts` | Gate navigation instrumentation on `SCREEN_NAVIGATION` feature + consent |
| **Framework integration** | `src/integrations/react/useRouterTracking.ts` | Call `navigationInstrumentation.onRouteChange()` on React Router v6 route change |
| | `src/integrations/next/useNextAppRouterTracking.ts` | Call `navigationInstrumentation.onRouteChange()` on Next.js app router route change |
| | `src/integrations/next/useNextPagesRouterTracking.ts` | Call `navigationInstrumentation.onRouteChange()` on Next.js pages router route change |
| | `src/integrations/next/instrumentation.ts` | Verify compatibility with navigation instrumentation |
| **Core wiring** | `src/exporters.ts` | No change (Logger + LoggerProvider already wired) |
| | `src/persistence/` | No change (spans stored in IDB like other signals) |
| | `src/consent.ts` | No change (navigation signals respect consent like other signals) |
| **Tests** | `src/__tests__/navigation-instrumentation.test.ts` | **New** unit tests (page load timing, SPA nav, screen.name resolution, rate limiting, SSR) |
| | `src/__tests__/screen-signals.integration.test.ts` | **New** integration tests (registry + gate + double-install + consent off-path) |
| **E2E & demo** | `examples/ecommerce-demo/src/` | Add/ensure routing UI (links/buttons that trigger route changes) |
| | `examples/ecommerce-demo/e2e/m4-screen-signals.spec.ts` | **New** E2E tests (positive: page load + SPA nav; gate-off: seed config disabled, zero exports; consent off) |
| | `examples/ecommerce-demo/.env.test` | Ensure `VITE_PULSE_FORMAT=json`, `VITE_PULSE_COMPRESSION=none` for fixture decoding |
| | `examples/ecommerce-demo/package.json` | Add `m4-screen-signals.spec.ts` to `e2e:web-sdk-gates` script |
| **Documentation & log** | `web-sdk-plan/v4-screen-signals/DESIGN.md` | Router overview + feature summary |
| | `web-sdk-plan/v4-screen-signals/test-run-log.md` | Test execution log (unit + E2E results) |
| **Backend** | `backend/server/src/main/java/.../Features.java` | Add `SCREEN_NAVIGATION` enum value |
| | `backend/server/src/main/java/.../DefaultSdkConfigTemplate.java` | Add expected feature name to default template + bump count |
| | `backend/server/src/test/.../DefaultSdkConfigTemplateTest.java` | Update test expectations |
| | `backend/ingestion/` | No schema changes (signals already ingested; ClickHouse has `ScreenName` materialized column) |

**Totals:** 2 new instrumentation files, 3 new test files, 1 new E2E spec, 3 backend Java files, 15+ existing files touched (config/wiring/integration).

**No new ClickHouse tables.** Signals stored in `otel_traces` with existing `ScreenName` materialization + index.
