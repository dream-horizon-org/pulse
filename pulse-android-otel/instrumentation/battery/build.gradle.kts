plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android battery instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.battery"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(projects.instrumentation.androidInstrumentation)
    implementation(projects.services)
    implementation(projects.common)
    implementation(projects.pulseUtils)
    implementation(projects.pulseSemconv)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)
    implementation(libs.androidx.core)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.sdk.extension.incubator)
    implementation(libs.kotlin.coroutines)
    testImplementation(projects.testCommon)
    testImplementation(libs.kotlin.coroutinesTest)
}
