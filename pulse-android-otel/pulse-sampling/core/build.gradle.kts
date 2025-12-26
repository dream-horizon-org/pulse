plugins {
    id("otel.android-library-conventions")
}

android {
    namespace = "com.pulse.sampling.core"
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(projects.testCommon)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(projects.pulseSamplingRemote)
    implementation(projects.pulseSamplingModels)
    testImplementation(testFixtures(projects.pulseSamplingModels))
    implementation(projects.pulseUtils)
}
