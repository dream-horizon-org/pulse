@file:Suppress("UnstableApiUsage")

plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "OpenTelemetry Android native (C/C++) crash instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.ccrash"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                // Match bugsnag-plugin-android-ndk (ProjectDefaults): unwindstack + EH across DSOs.
                arguments += listOf(
                    "-DANDROID_CPP_FEATURES=exceptions",
                    "-DANDROID_STL=c++_shared",
                )
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    api(projects.instrumentation.androidInstrumentation)
    implementation(projects.common)
    implementation(projects.services)
    implementation(projects.session)
    implementation(projects.pulseUtils)
    implementation(libs.kotlin.serialisation)
    implementation(projects.instrumentation.commonApi)
    api(platform(libs.opentelemetry.platform.alpha))
    api(libs.opentelemetry.api)
    implementation(libs.androidx.core)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.opentelemetry.sdk)
}

extra["pomName"] = "OpenTelemetry Android C/C++ crash instrumentation"
