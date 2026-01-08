plugins {
    id("otel.android-library-conventions")
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
    // testCommon is only used in tests, but declared as implementation for compilation
    // In the fused library, this will be included transitively
    implementation(projects.testCommon)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(projects.pulseSamplingRemote)
    implementation(projects.pulseSamplingModels)
    implementation(libs.kotlin.coroutines)
    testImplementation(testFixtures(projects.pulseSamplingModels))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.coroutinesTest)
    implementation(projects.pulseUtils)
}
