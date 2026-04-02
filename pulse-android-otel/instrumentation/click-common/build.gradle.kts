plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Shared helpers for View and Compose click instrumentations (gesture filter, widget log wiring)"

android {
    namespace = "io.opentelemetry.android.instrumentation.click.common"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)

    implementation(projects.pulseSemconv)
    implementation(projects.pulseUtils)
    implementation(libs.opentelemetry.semconv.incubating)
}
