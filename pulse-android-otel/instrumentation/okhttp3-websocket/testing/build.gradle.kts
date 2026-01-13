plugins {
    id("otel.android-app-conventions")
    id("net.bytebuddy.byte-buddy-gradle-plugin")
}

android {
    namespace = "io.opentelemetry.android.okhttp.websocket.testing"
}

dependencies {
    implementation(projects.testCommon)
    byteBuddy(projects.instrumentation.okhttp3Websocket.agent)
    implementation(projects.instrumentation.okhttp3Websocket.library)

    implementation(libs.okhttp)
    implementation(libs.opentelemetry.exporter.otlp)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
