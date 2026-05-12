# iOS · Crash

Captures unhandled exceptions and Mach / signal crashes and emits a `pulse.type = device.crash` log on next launch.

Brief: [../../../components/pulse-ios-otel.md](../../../components/pulse-ios-otel.md) · Peers: [../core/semconv](../core/semconv.md), [network](./network.md).

## Purpose

Detect hard crashes via a two-pronged approach: (1) MetricKit diagnostic payloads (`MXCrashDiagnostic`) for signal-level crashes, (2) an uncaught-exception handler for Objective-C/Swift NSExceptions. Buffer a minimal report to disk; emit on the next process launch because the current one is terminating.

## Source location

- `pulse-ios-otel/Sources/Instrumentation/Crashes/` — crash capture entry points
- `pulse-ios-otel/Sources/Instrumentation/MetricKit/` — MetricKit bridge (also handles ANR/hang diagnostics)
- Persistence: `pulse-ios-otel/Sources/PulseKit/PersistenceUtils.swift`

## Public surface

Public API on `PulseKit`:
- `PulseKit.start(configuration:)` auto-installs the crash handler when `configuration.enableCrashReporting == true`.
- No direct `reportCrash` API — fatal crashes are always terminal.

## Internal design

1. On `start`, install `NSSetUncaughtExceptionHandler` and subscribe to `MXMetricManager.shared.add(self)`.
2. On crash: serialize stack, reason, thread snapshot to disk (`Caches/pulse/crashes/*.json`). No network I/O — the process is dying.
3. On next launch, `PulseSignalProcessor` scans the crash dir, emits one OTLP log per record with:
   - `pulse.type = device.crash`
   - `exception.type`, `exception.message`, `exception.stacktrace`
   - `app.build_name`, `session.id` (the *previous* session id, preserved in a separate file)
4. Deletes processed files on successful export.

Invariants: never block the crashing thread; never allocate after signal; only defer emission until next launch.

## Dependencies

- MetricKit (iOS 13+) for signal/hang payloads.
- `PulseInstallationIdManager` for device id.
- `PulseUserSessionEmitter` for previous-session attribution.

## Data contracts

| Key | Value |
|---|---|
| `pulse.type` | `device.crash` or `non_fatal` (handled NSException) |
| `platform` | `ios` |
| `exception.type` / `.message` / `.stacktrace` | standard OTel semconv |
| `app.build_name` | from `Info.plist` |
| `session.id` | previous session's id (from persisted last-session file) |

## Tests

Unit tests in `pulse-ios-otel/Tests/.../CrashesTests.swift` use a mocked diagnostic payload and assert the emitted log shape. No live-crash tests in CI.

## History / decisions

Chose MetricKit over PLCrashReporter to avoid a native-linkage dependency and to let Apple symbolicate upstream. Signals that MetricKit misses (e.g. `abort()` during startup) fall through to the exception handler.

## Rebuild recipe

1. Create `Sources/Instrumentation/Crashes/CrashHandler.swift`.
2. In `start()`: install `NSSetUncaughtExceptionHandler`; add as MetricKit subscriber.
3. Write records to `Caches/pulse/crashes/`.
4. On SDK init of the next launch, scan dir → emit OTLP logs with the contract above → delete files on success.
5. Add a unit test that injects a fake payload and asserts log attributes.
