# Android — Instrumentation: App lifecycle (foreground/background, memory, battery, location)

## Purpose

Track process-level state transitions and ambient device signals that contextualize other telemetry: foreground/background transitions, memory pressure, battery state, network type and (when permitted) location.

## Source location

- `instrumentation/activity/.../ForegroundBackgroundTracker.kt` — process-wide foreground/background derived from activity callbacks.
- `instrumentation/memory/` — periodic memory snapshots → `pulse.type=memory`.
- `instrumentation/battery/` — battery state listener → `pulse.type=battery`.
- `instrumentation/location/` — `LocationManager` integration (opt-in, permission-gated).
- `instrumentation/android-log/` — bridge from `android.util.Log` into OTel logs (optional).
- `instrumentation/android-instrumentation/` — base `AndroidInstrumentation` SPI consumed by every module above.

## Public surface

DSL:

```kotlin
memory { enabled(true) }
battery { enabled(true) }
location { enabled(false) }   // opt-in
androidLog { enabled(false) }
```

## Internal design

- **Foreground/background**: `ForegroundBackgroundTracker` counts resumed activities; emits state-change events when count crosses zero (background ↔ foreground). Drives session timeout (`SessionIdTimeoutHandler` uses these events).
- **Memory**: periodic sampler reads `Debug.MemoryInfo` / `ActivityManager.getMemoryInfo()` and emits gauges or log records with `pulse.type=memory`.
- **Battery**: registers a `BroadcastReceiver` for `ACTION_BATTERY_CHANGED`, emits state on change.
- **Location**: opt-in; respects runtime permission; coarse-only by default. Emits resource-level attributes on the next signal rather than its own stream.

## Dependencies

- AndroidX lifecycle for process-level callbacks.
- `pulse-semconv` (`PulseTypeValues.MEMORY`, `BATTERY`).

## Data contracts

- `pulse.type` values: `memory`, `battery`.
- Foreground transitions are recorded as session-rollover triggers; they don't carry their own `pulse.type`.

## Tests

- Per-module `src/test/` directories (`memory`, `battery`, `location`, `activity` for foreground/background).

## History / decisions

- Location is opt-in and OFF by default — privacy/Play-Store policy alignment.
- `android-log` bridge is OFF by default to avoid log floods.

## Rebuild recipe

1. Implement `ProcessLifecycleObserver` in `ForegroundBackgroundTracker`.
2. Schedule memory sampling on a low-priority background thread (do not wake the main thread).
3. Register a single battery `BroadcastReceiver` per process.
4. Gate `location` on runtime permission + DSL flag.
5. Tag every signal with the appropriate `PulseTypeValues` constant.
