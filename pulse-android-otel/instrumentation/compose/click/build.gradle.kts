plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android compose click instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.compose.click"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.services)
    api(libs.opentelemetry.api)
    api(platform(libs.opentelemetry.platform.alpha))
    api(projects.instrumentation.androidInstrumentation)
    implementation(projects.instrumentation.clickCommon)
    implementation(libs.opentelemetry.sdk)

    compileOnly(libs.compose)
    implementation(libs.opentelemetry.api.incubator)
    implementation(libs.opentelemetry.instrumentation.apiSemconv)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(projects.pulseSemconv)

    testImplementation(projects.testCommon)
    testImplementation(projects.session)

    testImplementation(libs.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
