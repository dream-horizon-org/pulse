plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android location instrumentation library"

android {
    namespace = "io.opentelemetry.android.instrumentation.location.library"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.instrumentation.location.locationCore)
    implementation(projects.instrumentation.location.locationProcessors)
    implementation(projects.instrumentation.location.locationModels)
    api(projects.instrumentation.androidInstrumentation)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(project(":services"))
    testImplementation(projects.testCommon)
}
