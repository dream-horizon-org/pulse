# Pulse SDK Android

[![Continuous Build][ci-image]][ci-url]
[![Maven Central][maven-image]][maven-url]
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/open-telemetry/opentelemetry-android/badge)](https://scorecard.dev/viewer/?uri=github.com/open-telemetry/opentelemetry-android)
[![android api](https://img.shields.io/badge/Android_API-21-green.svg "Android min API 21")](VERSIONING.md)

**Production-grade observability for Android applications**

Real-time monitoring, crash reporting, ANR detection, and performance insights powered by OpenTelemetry.

---

## Status: Development

* [About](#about)
* [Getting Started](#getting-started)
* [Core Features](#core-features)
* [API Reference](#api-reference)
* [Advanced Configuration](#advanced-configuration)

---

## About

The Pulse Android SDK provides comprehensive Real User Monitoring (RUM) for Android applications. Built on top of the [OpenTelemetry Android SDK](https://github.com/open-telemetry/opentelemetry-android), it offers a simplified API for tracking custom events, errors, performance, and user behavior while automatically capturing native Android telemetry.

**Key Benefits:**
- 🚀 **Simple API**: High-level wrappers for common observability tasks
- 🔧 **Auto-instrumentation**: Automatic ANR, crash, and performance tracking
- 📊 **OpenTelemetry Native**: Full access to OpenTelemetry APIs when needed
- 🎯 **Production Ready**: Built on battle-tested OpenTelemetry infrastructure

---

## Getting Started

> **Requirements:**
> - Android minSdk: 21+
> - Android Gradle Plugin: 8.3.0+
> - If your minSdk < 26, enable [corelib desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring) and set `android.useFullClasspathForDexingTransform=true` in `gradle.properties`. See [#73](https://github.com/open-telemetry/opentelemetry-android/issues/73).

### Installation

Add the Pulse Android SDK dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    //...
    implementation("org.dreamhorizon:pulse-android-sdk:0.0.1-alpha")
    //...
}
```

### Initialization

Initialize the Pulse SDK in your `Application` class, as early as possible in `onCreate()`:

```kotlin
import android.app.Application
import android.util.Log
import com.pulse.android.sdk.PulseSDK
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

class MainApplication : Application() {
    var otelRum: OpenTelemetryRum? = null

    override fun onCreate() {
        super.onCreate()
        otelRum = initializePulse()
    }

    private fun initializePulse(): OpenTelemetryRum? = runCatching {
        PulseSDK.INSTANCE.initialize(
            application = this,
            endpointBaseUrl = "https://your-backend-url.com",
            globalAttributes = {
                Attributes.of(
                    AttributeKey.stringKey("app.version"), "1.0.0",
                    AttributeKey.stringKey("environment"), "production"
                )
            }
        ) {
            // Enable/disable specific instrumentations
            interaction {
                enabled(true)
                setConfigUrl { "http://10.0.2.2:8080/v1/interactions/all-active-interactions" }
            }
            activity { enabled(true) }
            fragment { enabled(false) }
            network { enabled(true) }
            anr { enabled(true) }
            slowRendering { enabled(true) }
        }
        PulseSDK.INSTANCE.getOtelOrNull()
    }.onFailure {
        Log.e("PulseSDK", "Initialization failed", it)
    }.getOrNull()
}
```

---

## Core Features

Pulse automatically captures critical Android telemetry:

- 🚨 **[Crash Reporting](./instrumentation/crash/)** - Native crashes and uncaught exceptions
- ⏱️ **[ANR Detection](./instrumentation/anr/)** - Application Not Responding events
- 🎬 **[Activity Lifecycle](./instrumentation/activity/)** - Screen transitions and app lifecycle
- 📱 **[Fragment Lifecycle](./instrumentation/fragment/)** - Fragment navigation and lifecycle
- 🖱️ **[View Interactions](./instrumentation/view-click/)** - Button clicks and user interactions
- 🐌 **[Slow/Frozen Renders](./instrumentation/slowrendering/)** - UI performance bottlenecks
- 🌐 **[Network Change Detection](./instrumentation/network/)** - Connectivity changes
- 💾 **Offline Buffering** - Store telemetry when offline, sync when online

---

## API Reference

Pulse provides a high-level API for common observability tasks.

### Initialization

```kotlin
PulseSDK.INSTANCE.initialize(
    application: Application,
    endpointBaseUrl: String,
    endpointHeaders: Map<String, String> = emptyMap(),
    globalAttributes: (() -> Attributes)? = null,
    diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)? = null,
    instrumentations: (InstrumentationConfiguration.() -> Unit)? = null
)

// Check if SDK is initialized
val isReady = PulseSDK.INSTANCE.isInitialized()
```

### User Identification

```kotlin
// Set user ID
PulseSDK.INSTANCE.setUserId("user-12345")

// Set individual user property
PulseSDK.INSTANCE.setUserProperty("email", "user@example.com")
PulseSDK.INSTANCE.setUserProperty("subscription", "premium")

// Set multiple user properties at once
PulseSDK.INSTANCE.setUserProperties {
    put("name", "John Doe")
    put("age", 30)
    put("verified", true)
}

// Clear user ID
PulseSDK.INSTANCE.setUserId(null)
```

### Event Tracking

```kotlin
// Track a custom event
PulseSDK.INSTANCE.trackEvent(
    name = "purchase_completed",
    observedTimeStampInMs = System.currentTimeMillis(),
    params = mapOf(
        "amount" to 99.99,
        "currency" to "USD",
        "itemCount" to 3
    )
)

// Track without attributes
PulseSDK.INSTANCE.trackEvent(
    name = "app_opened",
    observedTimeStampInMs = System.currentTimeMillis()
)
```

### Error Tracking

```kotlin
// Report a non-fatal error with message
PulseSDK.INSTANCE.trackNonFatal(
    name = "API request failed",
    observedTimeStampInMs = System.currentTimeMillis(),
    params = mapOf(
        "endpoint" to "/api/users",
        "statusCode" to 500,
        "retryAttempt" to 3
    )
)

// Report a non-fatal exception
try {
    riskyOperation()
} catch (e: Exception) {
    PulseSDK.INSTANCE.trackNonFatal(
        throwable = e,
        observedTimeStampInMs = System.currentTimeMillis(),
        params = mapOf(
            "context" to "payment_flow",
            "userId" to "user-123"
        )
    )
}
```

### Performance Tracing

```kotlin
// Automatic span management (recommended)
PulseSDK.INSTANCE.trackSpan(
    spanName = "database_query",
    params = mapOf("table" to "users", "operation" to "select")
) {
    // Your operation here
    val users = database.getUsers()
    processUsers(users)
}

// Manual span control
val endSpan = PulseSDK.INSTANCE.startSpan(
    spanName = "image_processing",
    params = mapOf("imageId" to "img-123", "format" to "jpeg")
)

try {
    processImage()
} finally {
    endSpan() // Always end the span
}
```

### Access OpenTelemetry APIs

For advanced use cases, access the underlying OpenTelemetry APIs:

```kotlin
// Get OpenTelemetry RUM instance
val otelRum = PulseSDK.INSTANCE.getOtelOrNull()

// Or throw if not initialized
val otelRum = PulseSDK.INSTANCE.getOtelOrThrow()

// Use OpenTelemetry APIs directly
val tracer = otelRum.getOpenTelemetry()
    .tracerProvider
    .get("my-instrumentation")

val span = tracer.spanBuilder("custom_operation").startSpan()
try {
    // Your operation
} finally {
    span.end()
}
```

---

## Advanced Configuration

### Global Attributes

Set attributes that will be included in all telemetry:

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com",
    globalAttributes = {
        Attributes.builder()
            .put("deployment.environment", "production")
            .put("service.version", BuildConfig.VERSION_NAME)
            .put("device.type", "mobile")
            .build()
    }
)
```

### Custom Headers

Add custom HTTP headers for backend authentication:

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com",
    endpointHeaders = mapOf(
        "Authorization" to "Bearer your-token",
        "X-API-Key" to "your-api-key"
    )
)
```

### Disk Buffering

Configure offline storage for telemetry:

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com",
    diskBuffering = {
        enabled(true)
        maxCacheSize(50 * 1024 * 1024) // 50 MB
    }
)
```

### Instrumentation Configuration

Enable or disable specific instrumentations:

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com"
) {
    // User interactions
    interaction {
        enabled(true)
        setConfigUrl { "http://10.0.2.2:8080/v1/interactions/all-active-interactions" }
    }
    
    // Activity lifecycle
    activity {
        enabled(true)
    }
    
    // Fragment lifecycle
    fragment {
        enabled(false) // Disable if not using fragments
    }
    
    // Network monitoring
    network {
        enabled(true)
    }
    
    // ANR detection
    anr {
        enabled(true)
    }
    
    // Slow/frozen render detection
    slowRendering {
        enabled(true)
    }
}
```

For detailed instrumentation configuration, see:
- [Interaction Instrumentation](./instrumentation/interaction/)
- [Activity Instrumentation](./instrumentation/activity/)
- [Fragment Instrumentation](./instrumentation/fragment/)
- [Network Instrumentation](./instrumentation/network/)
- [ANR Detection](./instrumentation/anr/)
- [Slow Rendering Detection](./instrumentation/slowrendering/)

### Session Configuration

Customize session behavior:

```kotlin
import io.opentelemetry.android.agent.session.SessionConfig

PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com",
    sessionConfig = SessionConfig.builder()
        .setSessionIdLength(32)
        .build()
)
```

---

## Important Notes

### StrictMode

For guidance on Android StrictMode violations (disk/network I/O warnings) triggered by SDK initialization, see [StrictMode Policy](./docs/STRICTMODE.md).

### Exporter Chain

The SDK performs asynchronous exporter initialization with in-memory buffering and optional disk buffering. See [Exporter Chain Documentation](./docs/EXPORTER_CHAIN.md) for details.

---

For more information about roles:
- [Maintainer Role](https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md#maintainer)
- [Approver Role](https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md#approver)

---

[ci-image]: https://github.com/open-telemetry/opentelemetry-android/actions/workflows/build.yaml/badge.svg
[ci-url]: https://github.com/open-telemetry/opentelemetry-android/actions?query=workflow%3Abuild+branch%3Amain
[maven-image]: https://maven-badges.sml.io/maven-central/io.opentelemetry.android/android-agent/badge.svg
[maven-url]: https://maven-badges.sml.io/maven-central/io.opentelemetry.android/android-agent