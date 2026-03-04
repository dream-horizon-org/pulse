plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Pulse SDK for mobile observability"

android {
    namespace = "com.pulse.android.sdk"
}

kotlin {
    explicitApi()
}

dependencies {
    api(projects.androidAgent)
    implementation(projects.pulseAndroidSdkInternal)
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
}
