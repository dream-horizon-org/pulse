plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android View click library instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.view.click"

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

    implementation(libs.opentelemetry.instrumentation.apiSemconv)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.opentelemetry.api.incubator)
    implementation(projects.pulseSemconv)

    testImplementation(projects.testCommon)
    testImplementation(projects.session)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
