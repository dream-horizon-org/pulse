# instrumentations/click

## 1. Purpose

Capture user clicks/taps as OTLP **logs** with `pulse.type = app.click`, clustering rapid repeats into a single rage-click event. Mirrors Android `ClickInstrumentation` + `ClickEventBuffer`.

## 2. Source location

- `pulse-web-otel/src/instrumentations/clicks.ts` — `ClicksInstrumentation`
- `pulse-web-otel/src/instrumentations/click-rage-buffer.ts` — rage clustering buffer
- `pulse-web-otel/src/instrumentations/click-target.ts` — DOM target inspection (composed path, widget id/name, click context label)

## 3. Public surface

```ts
class ClicksInstrumentation implements PulseInstrumentation {
  readonly name = "clicks";
  install(sdk: SdkContext): void;
  uninstall(): void;
}
```

Configured via `instrumentations.clicks.{ enabled, ... }` in `PulseWebConfig`. Gated by `PulseFeature.CLICK`.

## 4. Internal design

- `install()` attaches a single `click` listener (capture phase) to `document`; also a `visibilitychange` listener to flush the buffer on tab hide.
- For each event:
  1. `eventComposedPath` + `resolveInteractiveElement` to find the closest semantic target (button, anchor, role).
  2. `widgetIdFromElement` / `widgetNameFromElement` derive `app.widget.id` / `app.widget.name` (data-* attrs, aria-label, text content).
  3. `buildClickContextLabel` walks up the tree for `app.click.context` (form, section).
  4. Push into `ClickEventBuffer`: emits singleton tap after a debounce, or a rage event if N clicks land within window W on the same target.
- Emission is an OTLP log via `logs.getLogger("pulse-web-clicks")`, body `app.widget.click`.

## 5. Dependencies

- `@opentelemetry/api-logs`
- DOM APIs: `Event.composedPath`, `document.addEventListener`

## 6. Data contracts

`pulse.type = app.click`; log body `app.widget.click`. Attributes (`PulseWebSemconv.AttributeKey`):

- `click.type` (`single` / `rage` from `ClickTypeValue`)
- `click.is_rage`, `click.rage_count`
- `app.widget.name`, `app.widget.id`, `app.click.context`
- `app.screen.coordinate.x/y` and normalised `nx/ny` (relative to viewport)
- `device.screen.width`, `device.screen.height`
- `screen.name` (injected by global attrs processor)

## 7. Tests

- `src/__tests__/clicks-instrumentation.test.ts`
- E2E: `examples/ecommerce-demo/e2e/m3-clicks.spec.ts`, `m3-ch.spec.ts` (ClickHouse assertions)

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/clicks/SPEC.md`. Rage detection thresholds (`resolveClickRageConfig`) match the Android defaults; the `rageImmediate` flag exists so demos / tests can disable the trailing debounce.

## 9. Rebuild recipe

1. Implement `click-target.ts` helpers (pure, no side effects).
2. Build `ClickEventBuffer` as a stateful queue keyed by target signature with a timer.
3. In `ClicksInstrumentation.install()`, register the capture listener and pump events through the buffer.
4. Emit via `logs.getLogger` with the keys in §6.
