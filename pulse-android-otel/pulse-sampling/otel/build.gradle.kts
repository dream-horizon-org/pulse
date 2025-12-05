plugins {
    id("otel.android-library-conventions")
}

android {
    namespace = "com.pulse_sampling.otel"
}

kotlin {
    explicitApi()
}
