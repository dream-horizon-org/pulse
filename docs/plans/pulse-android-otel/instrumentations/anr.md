# Android — Instrumentation: ANR

## Purpose

Detect Application Not Responding conditions (main thread blocked for too long) and emit `pulse.type=device.anr` with the main thread's stack trace.

## Source location

`pulse-android-otel/instrumentation/anr/src/main/java/io/opentelemetry/android/instrumentation/anr/`:

- `AnrInstrumentation.kt` — `@AutoService(AndroidInstrumentation::class)` entry point.
- `AnrWatcher.kt` — background watchdog probing the main looper.

## Public surface

```kotlin
anr { enabled(true) }
```

DSL configuration also accepts threshold tweaks via `AnrReporterConfiguration` (`android-agent/.../dsl/instrumentation/AnrReporterConfiguration.kt`).

## Internal design

1. A background scheduler periodically posts a no-op runnable to the main looper and checks whether it ran within a threshold (default ~5 s aligned with Android ANR semantics).
2. If the deadline is missed, `AnrWatcher` captures `Looper.getMainLooper().thread.stackTrace` and emits a log record (`pulse.type=device.anr`) with the captured stack as `exception.stacktrace`.
3. Each ANR is debounced — a single sustained block produces one record, not a storm.

## Dependencies

- `pulse-semconv` (`PulseTypeValues.ANR`).
- OTel Android `common-api` instrumentation contract.

## Data contracts

- `pulse.type = device.anr`
- `exception.stacktrace` = main-thread stack at detection time.
- Duration attributes (`anr.duration_ms`) where available.

## Tests

`instrumentation/anr/src/test/` covers the watchdog logic with a fake clock.

## History / decisions

- Detection is heuristic; we deliberately don't use `ApplicationExitInfo` as the sole source because that's only available on Android 11+.
- ANR is Android-only; iOS uses MetricKit hang detection (see iOS plan).

## Rebuild recipe

1. Schedule periodic main-looper probes from a single background thread.
2. On missed probe, snapshot main-thread stack and emit a log record via the SDK's `Logger`.
3. Bypass sampling for `pulse.type=device.anr`.
4. Honour the DSL toggle and respect consent.
