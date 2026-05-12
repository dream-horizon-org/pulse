# iOS · Screen

Emits `screen_load`, `screen_interactive`, and `screen_session` spans keyed off UIViewController lifecycle.

Brief: [../../../components/pulse-ios-otel.md](../../../components/pulse-ios-otel.md) · Peers: [../core/semconv](../core/semconv.md), [session](./session.md), [interaction](./interaction.md).

## Source location

- `pulse-ios-otel/Sources/PulseKit/UIViewControllerSwizzler.swift`
- `pulse-ios-otel/Sources/PulseKit/VisibleScreenTracker.swift`
- `pulse-ios-otel/Sources/PulseKit/ScreenAttributesSpanProcessor.swift`
- `pulse-ios-otel/Sources/PulseKit/ScreenAttributesLogRecordProcessor.swift`

## Public surface

Installed auto by `PulseKit.start`. Disable: `configuration.enableScreenInstrumentation = false`.
Manual override: `PulseKit.setScreenName(_:)` (useful for container VCs).

## Internal design

Swizzles `UIViewController.viewDidLoad / viewWillAppear / viewDidAppear / viewWillDisappear`:
- `viewDidLoad` → start `screen_load` span.
- `viewDidAppear` → end `screen_load`; start `screen_interactive` (closes when runloop reports 2 consecutive idle frames).
- `viewWillAppear` → start `screen_session`.
- `viewWillDisappear` → end `screen_session` with duration.

`VisibleScreenTracker` keeps the topmost VC for downstream `screen.name` attribution on clicks/networks.

## Data contracts

| Span | `pulse.type` | Key attributes |
|---|---|---|
| screen_load | `screen_load` | `screen.name`, `duration_ms` |
| screen_interactive | `screen_interactive` | `screen.name`, `time_to_interactive_ms` |
| screen_session | `screen_session` | `screen.name`, `session_duration_ms` |

All carry `platform=ios` + `session.id`.

## Tests

`Tests/.../ScreenInstrumentationTests.swift` spins up a fake VC hierarchy and asserts span ordering + durations.

## History / decisions

Container VCs (UINavigationController, tab controllers) are filtered by default — only leaf content VCs emit screen signals; users can override with `setScreenName`.

## Rebuild recipe

1. Swizzle four lifecycle methods on `UIViewController`.
2. Track open spans in an NSMapTable keyed by VC instance.
3. Hook a display-link for TTI detection.
4. Expose a `setScreenName` escape hatch for container/custom cases.
