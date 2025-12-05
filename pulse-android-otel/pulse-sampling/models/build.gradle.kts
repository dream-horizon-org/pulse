plugins {
    id("otel.android-library-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

android {
    namespace = "com.pulse.sampling.models"
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.serialisation)
}
