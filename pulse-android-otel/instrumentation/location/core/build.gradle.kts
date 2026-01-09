plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "OpenTelemetry Android location core"

android {
    namespace = "io.opentelemetry.android.instrumentation.location.core"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

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
    implementation(projects.instrumentation.location.locationModels)
    api(projects.pulseUtils)
    implementation(libs.androidx.core)
    implementation(libs.play.services.location)
    api(libs.kotlin.coroutines)
    implementation(libs.kotlin.serialisation)
    testImplementation(projects.testCommon)
    testImplementation(libs.kotlin.coroutinesTest)
}
