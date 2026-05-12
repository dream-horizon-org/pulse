# iOS · Session

Emits `session.start` / `session.end` logs and propagates `session.id` onto every downstream signal.

Brief: [../../../components/pulse-ios-otel.md](../../../components/pulse-ios-otel.md) · Peers: [../core/semconv](../core/semconv.md), [screen](./screen.md), [crash](./crash.md).

## Source location

- `pulse-ios-otel/Sources/Instrumentation/Sessions/`
- `pulse-ios-otel/Sources/PulseKit/PulseUserSessionEmitter.swift`
- `pulse-ios-otel/Sources/Instrumentation/AppLifecycle/` — feeds foreground/background transitions

## Public surface

- `PulseKit.setUserId(_:)` — ties user id to the active session.
- `PulseKit.endSession()` — manual end (rare; framework usually handles it).
- Auto-installed; session boundaries tied to app foreground/background.

## Internal design

1. On first foreground after cold launch: create UUIDv7, persist to disk, emit `session.start`.
2. Session stays active across backgrounds < 30 min (configurable). On `applicationDidEnterBackground`, start an idle timer; on `applicationWillEnterForeground` within the window, keep the same session.
3. On idle expiry or explicit `endSession`: emit `session.end` with `duration_ms`.
4. `session.id` injected into every emitted span/log via `GlobalAttributesSpanProcessor` and `GlobalAttributesLogRecordProcessor`.

## Data contracts

| Signal | `pulse.type` | Attributes |
|---|---|---|
| Start log | `session.start` | `session.id`, `user.id` (nullable), `platform=ios`, `app.build_name` |
| End log | `session.end` | `session.id`, `session.duration_ms` |
| All other | — | `session.id` always attached |

## Tests

`Tests/.../SessionEmitterTests.swift` covers: cold-start emit, background-foreground bounce, idle expiry, manual end.

## History / decisions

Idle window defaults to 30 min to match web SDK + Android parity. UUIDv7 (time-sortable) to help ClickHouse sort sessions cheaply.

## Rebuild recipe

1. Add `PulseUserSessionEmitter` that owns `currentSessionId`.
2. Hook `UIApplication` lifecycle notifications.
3. Attach processors that write `session.id` onto every span/log.
4. Persist last-session-id so crash reporter can attribute `device.crash` correctly on next launch.
