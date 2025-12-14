@file:Suppress("UnstableApiUsage")

plugins {
    id("otel.android-library-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

android {
    namespace = "com.pulse.sampling.models"


    testFixtures {
        enable = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.serialisation)
}
