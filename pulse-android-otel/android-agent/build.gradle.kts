plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

android {
    namespace = "io.opentelemetry.android.agent"
}

dependencies {
    api(projects.core)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.instrumentation.api)
    implementation(projects.common)
    implementation(projects.session)
    implementation(projects.services)
    implementation(libs.opentelemetry.exporter.otlp)

    // Default instrumentations:
    api(projects.instrumentation.activity)
    api(projects.instrumentation.anr)
    api(projects.instrumentation.crash)
    api(projects.instrumentation.fragment)
    api(projects.instrumentation.network)
    api(projects.instrumentation.slowrendering)
    api(projects.instrumentation.startup)
    api(projects.instrumentation.sessions)

    // todo move this to pulse SDK
    api(projects.instrumentation.interaction.interactionLibrary)
    api(projects.instrumentation.interaction.interactionCore)

    testImplementation(libs.robolectric)
}

extra["pomName"] = "OpenTelemetry Android Agent"
description =
    "A library that contains all the commonly needed instrumentation for Android apps in a " +
    "convenient way with minimum configuration needed."
