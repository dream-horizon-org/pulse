plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Interaction library instrumentation for Android"

android {
    namespace = "io.opentelemetry.android.instrumentation.interaction.library"
}

dependencies {
    implementation(projects.instrumentation.interaction.interactionCore)
    implementation(projects.pulseSemconv)
    implementation(projects.pulseUtils)
    api(projects.instrumentation.androidInstrumentation)
    api(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.context)
    implementation(libs.opentelemetry.instrumentation.apiSemconv)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.opentelemetry.instrumentation.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.kotlin.coroutines)
}