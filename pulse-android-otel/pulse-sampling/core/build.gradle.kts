plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

android {
    namespace = "com.pulse.sampling.core"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(projects.pulseAndroidApi)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(projects.pulseSamplingRemote)
    implementation(projects.pulseSamplingModels)
    implementation(libs.kotlin.coroutines)
    testImplementation(testFixtures(projects.pulseSamplingModels))
    testImplementation(testFixtures(projects.pulseUtils))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.coroutinesTest)
    implementation(projects.pulseUtils)
}
