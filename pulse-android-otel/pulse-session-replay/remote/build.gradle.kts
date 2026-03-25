@file:Suppress("UnstableApiUsage")

plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description =
    "Pulse Session Replay HTTP client (Retrofit/OkHttp) — mirrors pulse-sampling-remote layering"

android {
    namespace = "com.pulse.android.sdk.replay.remote"

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
    implementation(projects.pulseUtils)
    api(libs.okhttp)
    implementation(libs.retrofit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.assertj.core)
}
