plugins {
    id("otel.android-app-conventions")
    id("net.bytebuddy.byte-buddy-gradle-plugin")
}

android {
    namespace = "io.opentelemetry.android.httpurlconnection.test"
}

dependencies {
    byteBuddy(projects.instrumentation.httpurlconnection.agent)
    implementation(projects.instrumentation.httpurlconnection.library)
    implementation(projects.testCommon)
    androidTestImplementation(libs.assertj.core)
}
