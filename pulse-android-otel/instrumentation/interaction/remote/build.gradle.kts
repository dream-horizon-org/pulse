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
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinSerialisation)
    implementation(libs.kotlin.serialisation)
}
