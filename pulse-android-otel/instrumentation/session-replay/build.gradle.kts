import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
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

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(projects.pulseUtils)
    implementation(libs.findLibrary("androidx-core").get())
    implementation("com.squareup.curtains:curtains:1.2.5")
    implementation(libs.findLibrary("findbugs-jsr305").get())
    implementation(libs.findLibrary("androidx-annotation").get())
    compileOnly(libs.findLibrary("compose").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testImplementation(libs.findBundle("mocking").get())
    testImplementation(libs.findBundle("junit").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("okhttp-mockwebserver").get())
}
