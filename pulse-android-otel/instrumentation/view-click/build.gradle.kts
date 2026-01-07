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

    implementation(libs.opentelemetry.instrumentation.apiSemconv)
    implementation(libs.opentelemetry.semconv.incubating)
    implementation(libs.opentelemetry.api.incubator)

    testImplementation(projects.testCommon)
    testImplementation(projects.session)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
