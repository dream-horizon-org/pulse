@file:Suppress("UnstableApiUsage")

plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

android {
    namespace = "com.pulse.sampling.remote"
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.pulseSamplingModels)
    implementation(projects.pulseUtils)
    api(libs.okhttp)
    api(libs.kotlin.serialisation)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinSerialisation)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.coroutinesTest)
}
