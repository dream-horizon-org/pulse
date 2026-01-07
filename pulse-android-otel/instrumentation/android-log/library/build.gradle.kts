plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android Log library instrumentation for Android"

android {
    namespace = "io.opentelemetry.android.log.library"
}

dependencies {
    api(projects.instrumentation.androidInstrumentation)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)

    implementation(libs.opentelemetry.instrumentation.apiSemconv)
    implementation(libs.opentelemetry.api.incubator)
}
