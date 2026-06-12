@file:Suppress("UnstableApiUsage")

plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "Interaction core library for Android"

android {
    namespace = "com.pulse.android.remote"

    buildFeatures {
        buildConfig = true
    }
    testFixtures {
        enable = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.pulseUtils)
    // Explicit since pulse-utils now declares okhttp as implementation (not api) — its okhttp
    // types no longer propagate onto this module's compile classpath.
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinSerialisation)
    implementation(libs.kotlin.serialisation)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.coroutinesTest)
}
