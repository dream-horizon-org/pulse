plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Internal module for Pulse utils for mobile observability"

android {
    namespace = "com.pulse.utils"
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
}