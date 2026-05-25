plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "OpenTelemetry android internal services"

android {
    namespace = "io.opentelemetry.android.internal.services"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.common)
    implementation(projects.pulseUtils)
    implementation(projects.session)

    implementation(libs.androidx.core)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.kotlin.serialisation)
    implementation(libs.kotlin.coroutines)
    implementation(libs.opentelemetry.semconv.incubating)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.navigation.fragment)
    testImplementation(libs.androidx.junit.ktx)
}
