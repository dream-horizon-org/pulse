plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Pulse Android API - utils for mobile observability"

android {
    namespace = "com.pulse.android.api"
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
}
