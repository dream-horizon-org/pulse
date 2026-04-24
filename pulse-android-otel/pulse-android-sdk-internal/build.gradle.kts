plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Pulse SDK internal implementation for mobile observability"

android {
    namespace = "com.pulse.android.sdk.internal"

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
    // using api as we are exposing the pulse.api types
    api(projects.pulseAndroidApi)
    implementation(projects.androidAgent)
    implementation(projects.services)
    implementation(projects.common)
    implementation(projects.pulseSemconv)
    implementation(projects.pulseSamplingModels)
    implementation(projects.pulseSamplingCore)
    implementation(projects.pulseUtils)
    implementation(projects.instrumentation.interaction.interactionLibrary)
    implementation(projects.instrumentation.location.locationProcessors)
    implementation(projects.instrumentation.sessionReplay)
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.exporter.otlp) {
        // See android-agent: avoid okhttp-jvm on Android consumer classpaths (duplicate okhttp3.*).
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
    }
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.kotlin.serialisation)
    implementation(projects.instrumentation.viewClick)
    implementation(projects.instrumentation.compose.click)
    testImplementation(testFixtures(projects.pulseUtils))
    testImplementation(testFixtures(projects.pulseSamplingModels))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.coroutinesTest)
}
