plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "Pulse Session Replay wire models (kotlinx.serialization snapshot payload types) — mirrors pulse-sampling-models layering"

android {
    namespace = "com.pulse.android.sdk.replay.models"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.serialisation)
}
