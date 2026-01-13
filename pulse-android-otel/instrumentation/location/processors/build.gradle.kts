plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "OpenTelemetry Android location processors"

android {
    namespace = "io.opentelemetry.android.instrumentation.location.processors"

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.instrumentation.location.locationModels)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.semconv)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.androidx.core)
    implementation(libs.kotlin.serialisation)
    testImplementation(projects.testCommon)
}
