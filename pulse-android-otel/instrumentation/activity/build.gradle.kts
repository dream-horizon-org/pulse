plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android activity instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.activity"

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
    api(projects.instrumentation.commonApi)
    api(projects.instrumentation.androidInstrumentation)
    implementation(projects.services)
    implementation(projects.session)
    implementation(projects.common)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.androidx.core)
    implementation(libs.opentelemetry.instrumentation.api)
    testImplementation(libs.robolectric)
}
