plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "Internal module for Pulse utils for mobile observability"

android {
    namespace = "com.pulse.utils"

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.kotlin.serialisation)
    implementation(libs.kotlin.coroutines)
    implementation(libs.play.services.tasks)
    testImplementation(libs.bundles.junit)
    testImplementation(projects.testCommon)
}
