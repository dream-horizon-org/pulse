# Android — Instrumentation: Screen (Activity / Fragment / Startup / Slow rendering)

## Purpose

Measure how long screens take to become visible (`screen_load`), how long users dwell on them (`screen_session`), how cold/warm app startup behaves (`app_start`), and detect jank (`app.jank.slow` / `app.jank.frozen`).

## Source location

- `instrumentation/activity/src/main/java/io/opentelemetry/android/instrumentation/activity/`:
  - `ActivityLifecycleInstrumentation.kt` (@AutoService).
  - `ActivityCallbacks.kt`, `Pre29ActivityCallbacks.kt`, `ActivityTracer.kt`, `ActivityTracerCache.kt`, `ForegroundBackgroundTracker.kt`.
- `instrumentation/fragment/` — analogous Fragment lifecycle tracer.
- `instrumentation/startup/src/main/java/io/opentelemetry/android/instrumentation/startup/`:
  - `StartupInstrumentation.kt`, `SdkInitializationEvents.kt` (the latter `@AutoService(InitializationEvents::class)`).
- `instrumentation/slowrendering/src/main/java/io/opentelemetry/android/instrumentation/slowrendering/`:
  - `SlowRenderingInstrumentation.kt`, `EventJankReporter.kt`, `SpanBasedJankReporter.kt`, `PerActivityListener.kt`, `FrameDataHelper.kt`, `SpanFrameAttributesAppended.kt`.

## Public surface

DSL toggles:

```kotlin
activity { enabled(true) }
fragment { enabled(false) }
slowRendering { enabled(true) }
```

`StartupInstrumentation` runs unconditionally — startup signals are foundational.

## Internal design

- **Activity / Fragment**: `ActivityCallbacks` register `Application.ActivityLifecycleCallbacks` (Pre29 variant for older API levels). `ActivityTracer` opens a `Created` span on `onActivityPreCreated` → closes on `onActivityPostResumed`, tagged `pulse.type=screen_load`. A `ActivitySession` span runs from `onActivityResumed` to `onActivityPaused`, tagged `pulse.type=screen_session`. `Restarted` / `Paused` / `Stopped` spans cover re-appearance and disappearance flows.
- **Startup**: `StartupInstrumentation` measures from process start (or earliest available signal) to first `Activity` becoming visible; emits `pulse.type=app_start`. `SdkInitializationEvents` captures the SDK's own init breadcrumbs.
- **Slow rendering**: `PerActivityListener` hooks `FrameMetrics` (API 24+). `FrameDataHelper` classifies frames as slow/frozen using Android Vitals thresholds; `EventJankReporter` emits log records with `pulse.type=app.jank.slow` / `app.jank.frozen`; `SpanBasedJankReporter` attaches `app.frame.*` attributes to the currently-active screen span via `SpanFrameAttributesAppended`.

## Dependencies

- `pulse-semconv` (`SCREEN_LOAD`, `SCREEN_SESSION`, `APP_START`, `SLOW`, `FROZEN`).
- OTel Android `core`.
- AndroidX lifecycle (for fragment).

## Data contracts

- `pulse.type` values: `screen_load`, `screen_session`, `app_start`, `app.jank.slow`, `app.jank.frozen`.
- Attributes: `screen.name`, `activity.name`, `fragment.name`, `app.frame.total`, `app.frame.slow`, `app.frame.frozen`.

## Tests

- `instrumentation/activity/src/test/`, `instrumentation/fragment/src/test/`, `instrumentation/slowrendering/src/test/`, `instrumentation/startup/src/test/`.

## History / decisions

- Span naming (`Created` / `Restarted` / `Stopped`) mirrors iOS (`internal-docs/IOS_LIFECYCLE_SIGNALS.md`) so dashboards stay portable.
- Jank reported as both an event (for counts) and a span attribute (for correlation with screen load).

## Rebuild recipe

1. Register `ActivityLifecycleCallbacks`; open/close `Created` and `ActivitySession` spans at the canonical points.
2. Mirror for Fragments via `FragmentManager.FragmentLifecycleCallbacks`.
3. Add a `FrameMetrics` listener per activity (API 24+); aggregate per render window.
4. Stamp `pulse.type` from `PulseTypeValues` on every span/log.
