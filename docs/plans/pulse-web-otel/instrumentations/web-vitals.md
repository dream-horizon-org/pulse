# instrumentations/web-vitals

## 1. Purpose

Capture Core Web Vitals (LCP, INP, CLS, FCP, FID, TTFB) using Google's `web-vitals` library and emit each as an OTLP **log** with `pulse.type = web_vital`. Plan B in the SPEC (logs, not metrics) so dashboards can join by `session.id` + `screen.name`.

## 2. Source location

- `pulse-web-otel/src/instrumentations/web-vitals.ts` — `WebVitalsInstrumentation`
- `web-vitals` npm package (peer-ish, bundled)

## 3. Public surface

```ts
class WebVitalsInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.WEB_VITALS; // "web-vitals"
  install(sdk: SdkContext): void;
  uninstall(): void;
}
```

Gated by `PulseFeature.WEB_VITALS`.

## 4. Internal design

- Subscribes to `onCLS`, `onFCP`, `onFID`, `onINP`, `onLCP`, `onTTFB` from `web-vitals`.
- A shared `emit(metric)` closure builds attributes and calls `logger.emit({ body: PulseWebSemconv.LogBody.WEB_VITAL, attributes })`.
- Listener registration is idempotent because `InstrumentationRegistry.installAllCompleted` blocks repeat installs (web-vitals attaches global listeners that misbehave on double-register).
- Also tracks `visibilitychange` and `pageshow` to flush late-arriving metrics on page hide / BFCache restore.

## 5. Dependencies

- `web-vitals` (npm)
- `@opentelemetry/api-logs`

## 6. Data contracts

`pulse.type = web_vital`; log body `web_vital`. Attributes:

- `web_vital.name` (`LCP` | `INP` | `CLS` | `FCP` | `FID` | `TTFB`)
- `web_vital.value` (number)
- `web_vital.rating` (`good` | `needs-improvement` | `poor`)
- `web_vital.navigation_type` (when supplied by web-vitals)
- Inherits `session.id`, `screen.name` from global attrs processor.

## 7. Tests

- `src/__tests__/web-vitals-instrumentation.test.ts`
- E2E: `examples/ecommerce-demo/e2e/web-vitals.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/web-vitals/SPEC.md`. Plan B (logs) chosen over Plan A (metrics) for joinability with `session.id` and `screen.name`; metrics path remains available via the metrics-to-add remote-config rules.

## 9. Rebuild recipe

1. Add `web-vitals` dependency.
2. Build the `emit(metric)` shared helper.
3. Register all 6 callbacks; register visibility / pageshow listeners to drain.
4. Detach all listeners on `uninstall()`.
