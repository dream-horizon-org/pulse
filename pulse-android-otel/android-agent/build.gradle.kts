plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

android {
    namespace = "io.opentelemetry.android.agent"
}

dependencies {
    api(projects.core)
    implementation(projects.instrumentation.androidInstrumentation)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.instrumentation.api)
    implementation(projects.common)
    implementation(projects.session)
    implementation(projects.services)
    implementation(libs.opentelemetry.exporter.otlp) {
        // OTel Java uses okhttp-jvm; Android must use okhttp only or duplicate classes with pulse-utils.
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
    }

    // Default instrumentations:
    api(projects.instrumentation.activity)
    api(projects.instrumentation.anr)
    api(projects.instrumentation.crash)
    api(projects.instrumentation.fragment)
    api(projects.instrumentation.network)
    api(projects.instrumentation.slowrendering)
    api(projects.instrumentation.startup)
    api(projects.instrumentation.sessions)
    api(projects.instrumentation.sessionReplay)
    api(projects.instrumentation.memory)
    api(projects.instrumentation.battery)

    // todo move this to pulse SDK
    api(projects.instrumentation.interaction.interactionLibrary)
    api(projects.instrumentation.interaction.interactionCore)

    // Pulse dependencies for SDK name detection
    implementation(projects.pulseSamplingModels)
    implementation(projects.pulseSemconv)
    implementation(projects.pulseUtils)

    testImplementation(libs.robolectric)
}

extra["pomName"] = "OpenTelemetry Android Agent"
description =
    "A library that contains all the commonly needed instrumentation for Android apps in a " +
    "convenient way with minimum configuration needed."
