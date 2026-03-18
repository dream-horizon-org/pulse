plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Pulse Session Replay – screenshot capture, masking, throttling, lifecycle (no PostHog dependency)"

android {
    namespace = "com.pulse.android.sdk.replay"

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
    api(projects.instrumentation.androidInstrumentation)
    implementation(projects.session)
    implementation(projects.pulseUtils)
    implementation(libs.androidx.core)
    implementation(libs.curtains)
    implementation(libs.findbugs.jsr305)
    implementation(libs.androidx.annotation)
    compileOnly(libs.compose)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.mocking)
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(libs.gson)
    testImplementation(libs.gson)
}
