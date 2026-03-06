plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlinSerialisation)
}

description = "Internal module for Pulse utils for mobile observability"

@Suppress("UnstableApiUsage")
android {
    namespace = "com.pulse.utils"

    testFixtures {
        enable = true
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.opentelemetry.platform.alpha))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.kotlin.serialisation)
    implementation(libs.kotlin.coroutines)
    implementation(libs.play.services.tasks)
    // we are exposing okHttp types and having api enabled clients to use it without declaring okHttp dependency
    api(libs.okhttp)
    testImplementation(libs.bundles.junit)
    testImplementation(projects.testCommon)
    testFixturesImplementation(libs.opentelemetry.sdk.testing)
}
