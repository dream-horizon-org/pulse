plugins {
    id("otel.android-app-conventions")
    id("net.bytebuddy.byte-buddy-gradle-plugin")
}

android.namespace = "io.opentelemetry.android.okhttp3"

dependencies {
    byteBuddy(projects.instrumentation.okhttp3.agent)
    implementation(projects.instrumentation.okhttp3.library)
    implementation(libs.okhttp)
    implementation(libs.opentelemetry.exporter.otlp)
    androidTestImplementation(libs.okhttp.mockwebserver)
    implementation(projects.testCommon)
}
