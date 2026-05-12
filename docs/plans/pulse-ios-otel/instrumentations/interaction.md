# iOS · Interaction

Captures taps and emits `pulse.type = app.click` logs, with view-hierarchy attribution.

Brief: [../../../components/pulse-ios-otel.md](../../../components/pulse-ios-otel.md) · Peers: [../core/semconv](../core/semconv.md), [screen](./screen.md).

## Source location

- `pulse-ios-otel/Sources/Instrumentation/UIKitTap/`
- `pulse-ios-otel/Sources/Instrumentation/Interaction/`

## Public surface

Installed by `PulseKit.start`. Opt-out via `configuration.enableTapInstrumentation = false`.
Tagging: `UIView.accessibilityIdentifier` used as stable `target.id`.

## Internal design

Swizzles `UIWindow.sendEvent` to observe `UITouch.ended` on hit-test targets:
1. Resolve deepest hit-test view; walk up the view chain to find the first `UIControl` or view with an `accessibilityIdentifier`.
2. Build `target.path` (e.g. `UIView>UIButton#submit`).
3. Emit one log per tap.

Debounce: drops duplicate taps within 50 ms on the same target.

## Data contracts

- `pulse.type = app.click`
- `platform = ios`
- `target.id`, `target.type`, `target.label` (accessibility label), `target.path`
- `screen.name` (from `VisibleScreenTracker`)

## Tests

`Tests/.../TapInstrumentationTests.swift` synthesizes taps via `UIEvent` stubs and asserts log attributes.

## History / decisions

View-chain walk chosen over gesture-recognizer observation so non-UIKit gestures (custom overlays) still produce events.

## Rebuild recipe

1. Add a `UIWindow` subclass or swizzle `sendEvent(_:)`.
2. On touch-ended, resolve hit-test + accessibility path.
3. Emit log with the contract above.
4. Add 50 ms debounce keyed on `(target.id, screen.name)`.
