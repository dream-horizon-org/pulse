plugins {
    id("otel.android-app-conventions")
    id("net.bytebuddy.byte-buddy-gradle-plugin")
}

android {
    namespace = "io.opentelemetry.android.log.test"
}

dependencies {
    byteBuddy(projects.instrumentation.androidLog.agent)
    implementation(projects.instrumentation.androidLog.library)
    implementation(projects.testCommon)

    androidTestImplementation(libs.opentelemetry.instrumentation.apiSemconv)
    androidTestImplementation(libs.assertj.core)
}
