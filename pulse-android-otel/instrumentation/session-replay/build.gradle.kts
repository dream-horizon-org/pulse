plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "Pulse Session Replay – screenshot capture, masking, throttling, lifecycle (no PostHog dependency)"

android {
    namespace = "com.pulse.android.sdk.replay"

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
    api(projects.instrumentation.androidInstrumentation)
    api(projects.pulseSessionReplayRemote)
    implementation(projects.pulseSessionReplayModels)
    implementation(projects.session)
    implementation(projects.pulseUtils)
    implementation(libs.opentelemetry.sdk)
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
    testImplementation(libs.opentelemetry.sdk.testing)
    implementation(libs.kotlin.serialisation)
    testImplementation(libs.kotlin.serialisation)
    implementation(libs.kotlin.coroutines)
    testImplementation(libs.kotlin.coroutinesTest)
}
