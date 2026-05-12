# Android — Instrumentation: Crash

## Purpose

Capture uncaught JVM exceptions as OTel log records with `pulse.type=device.crash`, including stacktrace, thread state and OTel exception conventions.

## Source location

`pulse-android-otel/instrumentation/crash/src/main/java/io/opentelemetry/android/instrumentation/crash/`:

- `CrashReporterInstrumentation.kt` — `@AutoService(AndroidInstrumentation::class)` entry point.
- `CrashReportingExceptionHandler.kt` — `Thread.UncaughtExceptionHandler` chaining to the previous handler.
- `CrashReporter.kt` — converts a `CrashDetails` into a log record.
- `CrashDetails.kt` — data class (throwable + thread).
- `RuntimeDetailsExtractor.kt` — battery / memory / network snapshot at crash time.

## Public surface

Configured via the `InstrumentationConfiguration` DSL:

```kotlin
crash { enabled(true) }
```

No app-visible API beyond the toggle.

## Internal design

1. On install, `CrashReporterInstrumentation` replaces the global `Thread.defaultUncaughtExceptionHandler` with `CrashReportingExceptionHandler`, retaining the previous handler.
2. On crash: `RuntimeDetailsExtractor` snapshots runtime attrs, `CrashReporter` emits a synchronous log record with `pulse.type=device.crash` and OTel `exception.*` attributes.
3. The original handler is invoked afterwards so existing crash reporters still fire.
4. Synchronous flush via the OTLP exporter chain before the process dies; the disk buffer captures anything that doesn't make it on the wire.

## Dependencies

- `pulse-semconv` (`PulseTypeValues.CRASH`).
- OTel logs API.
- `pulse-utils` for runtime helpers.

## Data contracts

- `pulse.type = device.crash`
- `exception.type`, `exception.message`, `exception.stacktrace` (OTel conventions).
- Resource attributes inherited (`project.id`, `telemetry.sdk.name`, `platform=android`).

## Tests

`instrumentation/crash/src/test/` covers handler chaining and log record shape.

## History / decisions

- Always-on regardless of sampling config (see `core/sampling.md`).
- Chains the previous handler instead of replacing — interop with Sentry/Crashlytics is intentional.

## Rebuild recipe

1. Implement `AndroidInstrumentation` annotated with `@AutoService`.
2. In `install`, save existing handler, set a new `UncaughtExceptionHandler` that emits a log + delegates.
3. Use the SDK's `Logger` (instrumentation scope `io.opentelemetry.android.crash`).
4. Mark sampling as bypass for `pulse.type=device.crash`.
