plugins {
    // todo this could be plain java lib
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Pulse semconv for mobile observability"

android {
    namespace = "com.pulse.semconv"
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.instrumentation.interaction.interactionCore)
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.semconv)
}
