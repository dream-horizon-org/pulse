# Research 02 — OTel JS browser + Pulse SDK integration

## Objective

Map error signal paths through the Pulse Web SDK and identify lifecycle/gate touchpoints.

## Key touchpoints discovered

1. `ErrorInstrumentation` uses `@opentelemetry/api-logs` logger emit path and captures:
   - `window.error` as `device.crash`
   - `unhandledrejection` as `non_fatal`
2. `Pulse.reportException` and `Pulse.reportDeviceCrash` in `sdk.ts` are manual entrypoints.
3. Install gate path is owned by `InstrumentationRegistry.shouldInstall()`:
   - instrumentation key: `errors`
   - feature gate: `PulseFeature.JS_CRASH` (`js_crash`)
4. Contract constants come from `PulseWebSemconv` and should be asserted via exact keys/values in E2E.
5. E2E capture pipeline expects JSON OTLP body decode in fixture; `.env.test` must align.

## Risks identified pre-hardening

1. Existing E2E assertions often checked only presence, not finite numeric contract values.
2. `screen.name` and `session.id` were not consistently enforced on positive error paths.
3. Gate-off scenario for `js_crash` was missing required reset-proof-of-life sequence.

## Resulting hardening focus

- Strengthen E2E contract assertions and gate-off pattern.
- Add minimal demo controls for realistic edge-case triggering.
- Include the error E2E file in gate script.

