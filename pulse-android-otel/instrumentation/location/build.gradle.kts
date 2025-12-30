plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "OpenTelemetry Android location instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.location"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.semconv)
    implementation(libs.opentelemetry.semconv.incubating)
    api(projects.instrumentation.androidInstrumentation)
    api(projects.pulseUtils)
    implementation(projects.common)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.androidx.core)
    implementation(libs.play.services.location)
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.serialisation)
    testImplementation(projects.testCommon)
    testImplementation(projects.session)
}
