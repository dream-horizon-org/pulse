# PLAN-B — Error instrumentation via OTLP logs

## Scope

- SDK: `src/instrumentations/errors.ts`, `src/sdk.ts`, `src/instrumentation-registry.ts`
- Demo: `examples/ecommerce-demo/src/routes/ErrorDemo.tsx`
- E2E: `examples/ecommerce-demo/e2e/m3-errors.spec.ts`

## Signal contract

## 1) Unhandled JS error

- Trigger: `window.addEventListener("error", ...)`
- `pulse.type`: `device.crash`
- Required attrs:
  - `exception.type`
  - `exception.message`
  - `exception.stacktrace`
  - `error.filename`
  - `error.lineno`
  - `error.colno`
  - `url.path`
- Optional attrs:
  - `battery.percent`
  - `storage.free`
- Severity:
  - `severityText=FATAL`

## 2) Unhandled rejection

- Trigger: `window.addEventListener("unhandledrejection", ...)`
- `pulse.type`: `non_fatal`
- Required attrs:
  - `exception.type`
  - `exception.message`
  - `exception.stacktrace`
  - `url.path`
  - `non_fatal.is_manual=false`
- Severity:
  - `severityText=WARN`

## 3) Manual report

- Trigger: `PulseWeb.reportException(...)`
- `pulse.type`: `non_fatal`
- Required attrs:
  - same as non-fatal path
  - `non_fatal.is_manual=true`

## Lifecycle and gating

- Install via `InstrumentationRegistry.installAll()` when:
  - local instrumentation config allows `errors`
  - remote gate `PulseFeature.JS_CRASH` is enabled
- Uninstall removes `error` and `unhandledrejection` listeners and cached device-state listeners.
- Dedupe window is 5 seconds for auto-captured errors.

## E2E floor (required)

For positive-path log assertions:

1. exact `pulse.type`
2. truthy `session.id`
3. truthy `screen.name`
4. finite numeric values where expected (`error.lineno`, `error.colno`, optional battery/storage checks)
5. correct manual flag for non-fatal path

Gate-off scenario:
- seed SDK config with `js_crash` sample rate 0
- block active config fetch
- wait for `session.start` proof-of-life
- `otlp.reset()`
- trigger error actions
- assert zero `device.crash`/`non_fatal` exports

## E2E matrix

| Scenario | Type |
|----------|------|
| Uncaught error contract floor | P0 |
| Unhandled rejection contract floor | P0 |
| Manual reportException contract floor | P0 |
| Render error boundary crash path | P1 |
| Dedupe burst (within window) | P1 |
| Dedupe reset after 5s | P1 |
| String/undefined rejection normalization | P1 |
| Cross-origin script error skipped | P1 |
| Gate-off zero export with reset | P0 |
| Consent denied zero export | P0 |

