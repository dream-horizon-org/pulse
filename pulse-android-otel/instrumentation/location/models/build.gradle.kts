plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "OpenTelemetry Android location models"

android {
    namespace = "io.opentelemetry.android.instrumentation.location.models"

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.serialisation)
}
