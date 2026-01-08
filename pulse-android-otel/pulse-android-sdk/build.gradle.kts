plugins {
    id("otel.android-library-conventions")
}

description = "Pulse SDK for mobile observability"

android {
    namespace = "com.pulse.android.sdk"

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
    api(projects.androidAgent)
    implementation(projects.common)
    implementation(projects.pulseSemconv)
    implementation(projects.pulseUtils)
    implementation(projects.instrumentation.interaction.interactionLibrary)
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.kotlin.serialisation)
}
