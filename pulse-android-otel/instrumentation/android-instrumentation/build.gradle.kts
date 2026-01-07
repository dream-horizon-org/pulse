plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android Instrumentation Auto Service"

android {
    namespace = "io.opentelemetry.android.instrumentation"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.services)
    implementation(projects.session)

    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)
}
