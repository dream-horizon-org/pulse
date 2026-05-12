# instrumentations/error

## 1. Purpose

Capture unhandled JS errors (`window.onerror`) and unhandled promise rejections (`unhandledrejection`) plus the manual `Pulse.reportError()` API, and turn them into OTLP log records with `pulse.type = device.crash` or `non_fatal`. Mirrors Android `CrashReporter` + `NonFatalReporter`.

## 2. Source location

- `pulse-web-otel/src/instrumentations/errors.ts` — `ErrorInstrumentation`
- `pulse-web-otel/src/utils/error-stack.ts` — `errorFilenameFromStack`

## 3. Public surface

```ts
class ErrorInstrumentation implements PulseInstrumentation {
  readonly name = "errors";
  install(sdk: SdkContext): void;
  uninstall(): void;
}
```

Gated by `PulseFeature.JS_CRASH`. Also reachable via `Pulse.reportError(error, opts)` on the SDK facade.

## 4. Internal design

- `install()` attaches `error` and `unhandledrejection` listeners on `window`.
- Prefetches device state in the background:
  - `navigator.getBattery()` → `battery.percent`, plus a `levelchange` listener kept on `batteryRef` so it can be detached on `uninstall()`.
  - `navigator.storage.estimate()` → `storage.free`.
- Deduplication: `dedupeCache` keyed by error signature with `DEDUPE_WINDOW_MS = 5_000` — drops duplicates inside the window.
- On capture, emits an OTLP log via `logs.getLogger("pulse-web-errors")` with severity `ERROR`.
- Unhandled errors → `pulse.type = device.crash`; manual reports → `non_fatal` with `non_fatal.is_manual = true`; `non_fatal.type` carries the manual category.

## 5. Dependencies

- `@opentelemetry/api-logs`, `@opentelemetry/api` (`context`)
- Browser APIs: `Battery Status`, `Storage.estimate`

## 6. Data contracts

`pulse.type ∈ { device.crash, non_fatal }`. Attribute keys: `exception.type`, `exception.message`, `exception.stacktrace`, `error.filename`, `error.lineno`, `error.colno`, `non_fatal.type`, `non_fatal.is_manual`, `battery.percent`, `storage.free`. `event.name` from `PulseWebSemconv.LogEventName`.

## 7. Tests

- `src/__tests__/m1.test.ts`, `src/__tests__/sdk-public-methods.test.ts` (manual reportError)
- E2E: `examples/ecommerce-demo/e2e/m3-errors.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/errors/SPEC.md`. The battery listener is explicitly retained on the class so `uninstall()` can detach it — earlier versions leaked listeners on SDK restart.

## 9. Rebuild recipe

1. Add `errorFilenameFromStack` utility that parses the first `at file:line:col` frame.
2. In `install()`, attach `window.addEventListener("error", ...)` and `"unhandledrejection"`.
3. Prefetch battery + storage; keep references to detach on `uninstall`.
4. Implement dedupe map with TTL eviction at emit time.
5. Build attributes from `PulseWebSemconv.AttributeKey` and emit log with severity `ERROR`.
