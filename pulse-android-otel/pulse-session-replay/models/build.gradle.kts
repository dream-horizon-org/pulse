plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Pulse Session Replay wire models (Gson snapshot payload types) — mirrors pulse-sampling-models layering"

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
    implementation(libs.gson)
}
