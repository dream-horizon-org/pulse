# pulse-android-otel — Component Brief

## What

Production-grade Android RUM SDK built on top of the OpenTelemetry Android upstream. Public surface is `PulseSDK.INSTANCE` (Kotlin singleton) exposing `initialize`, `trackEvent`, `trackNonFatal`, `trackSpan`, user/consent APIs and a DSL for toggling individual auto-instrumentations.

## Path & tech stack

- Path: `/Users/ujjwal.bagrania/Desktop/pulse/pulse-android-otel/`
- Stack: Kotlin (explicit API), OpenTelemetry Android SDK, Gradle multi-module (`settings.gradle.kts` enumerates 30+ subprojects under `core/`, `instrumentation/*`, `pulse-*`).
- Min SDK: Android API 21; AGP 8.3.0+.
- Distribution: Maven Central — `org.dreamhorizon:pulse-android-sdk:<version>`.

## Build commands

From `pulse-android-otel/`:

```bash
./gradlew build                          # full build + checks
./gradlew :pulse-android-sdk:assemble    # SDK AAR only
./gradlew test                           # unit tests
./gradlew :demo-app:installDebug         # demo APK
```

See `pulse-android-otel/README.md` and `pulse-android-otel/VERSIONING.md`.

## Package roots (must never mix)

- `io.opentelemetry.android.*` — OpenTelemetry upstream and OTel-side instrumentations under `instrumentation/<name>/src/main/java/io/opentelemetry/android/instrumentation/<name>/`.
- `com.pulse.*` — Pulse-specific surface: `com.pulse.android.sdk`, `com.pulse.android.api`, `com.pulse.semconv`, `com.pulse.sampling.*`, `com.pulse.utils`.

The two namespaces are physically separated by Gradle module. Cross-imports happen only at well-defined boundaries (e.g. semconv consumed everywhere; sampling consumed by SDK glue).

## Auto-discovery

Every auto-instrumentation registers itself via JDK `ServiceLoader` using AutoService:

```kotlin
@AutoService(AndroidInstrumentation::class)
class CrashReporterInstrumentation : AndroidInstrumentation { ... }
```

The `OpenTelemetryRum` builder loads all `AndroidInstrumentation` services at init time. `StartupInstrumentation` additionally registers `@AutoService(InitializationEvents::class)`.

## Key files

- `pulse-android-sdk/src/main/java/com/pulse/android/sdk/PulseSDK.kt` — public interface.
- `pulse-android-sdk/src/main/java/com/pulse/android/sdk/PulseSDKAdapter.kt` — facade impl.
- `pulse-android-sdk-internal/` — internal implementation (`PulseSDKInternal`).
- `pulse-android-api/` — public API surface (`PulseDataCollectionConsent`, `PulseBeforeSendData`).
- `pulse-semconv/src/main/java/com/pulse/semconv/PulseAttributes.kt` — attribute keys, `PulseTypeValues`, `PulseSdkNames`.
- `pulse-sampling/{core,models,remote}/` — remote-config-driven signal sampling.
- `android-agent/src/main/kotlin/io/opentelemetry/android/agent/` — `OpenTelemetryRumInitializer`, session manager, DSL.
- `core/src/main/java/io/opentelemetry/android/export/` — `BufferDelegating{Span,Log,Metric}Exporter`.
- `instrumentation/{crash,anr,activity,fragment,network,sessions,interaction,view-click,compose,slowrendering,startup,memory,battery,location,httpurlconnection,okhttp3,okhttp3-websocket,session-replay,...}/`.
- `docs/EXPORTER_CHAIN.md`, `docs/STRICTMODE.md`.

## Cross-SDK parity contract

Every emitted signal carries a `platform` resource attribute and a `pulse.type` span/log attribute drawn from `PulseAttributes.PulseTypeValues`. Android-specific values aligned with the web/iOS SDKs:

| `pulse.type` | Source |
|---|---|
| `session.start` / `session.end` | `instrumentation/sessions` |
| `device.crash` | `instrumentation/crash` |
| `device.anr` | `instrumentation/anr` (Android-only) |
| `non_fatal` | `PulseSDK.trackNonFatal` |
| `network.*` | `instrumentation/network`, `okhttp3`, `httpurlconnection` |
| `app.click` | `instrumentation/view-click`, `instrumentation/compose` |
| `screen_load` | `instrumentation/activity`, `fragment`, `startup` |
| `screen_session` | `instrumentation/activity`, `fragment` |
| `app.jank.frozen` / `app.jank.slow` | `instrumentation/slowrendering` |
| `interaction` | `instrumentation/interaction` |
| `custom_event` | `PulseSDK.trackEvent` |

The canonical enum lives in `pulse-semconv/.../PulseAttributes.kt` (`PulseTypeValues`).

## Plan handbook

See `/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/pulse-android-otel/index.md` for the per-module rebuild guide.
