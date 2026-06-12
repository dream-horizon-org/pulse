plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android sessions instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.sessions"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha))
    api(projects.instrumentation.androidInstrumentation)
    implementation(libs.opentelemetry.api.incubator)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.semconv.incubating)
    api(projects.core)
    api(projects.common)
    api(projects.session)
}
