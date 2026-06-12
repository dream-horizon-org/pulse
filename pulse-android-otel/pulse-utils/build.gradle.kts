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
    // okhttp is bundled at runtime but not exposed on consumers' compile classpath.
    // Consumers bring their own okhttp version (RN: 4.x; direct Android: whatever); Gradle's
    // "highest wins" means our pinned 4.12.0 only matters if no other contributor exists.
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    testImplementation(libs.bundles.junit)
    testImplementation(projects.testCommon)
    testFixturesImplementation(libs.opentelemetry.sdk.testing)
}
