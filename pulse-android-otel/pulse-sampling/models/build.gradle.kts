@file:Suppress("UnstableApiUsage")

plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

android {
    namespace = "com.pulse.sampling.models"

    testFixtures {
        enable = true
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.serialisation)
    implementation(projects.pulseUtils)
}
