plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "Interaction core library for Android"

android {
    namespace = "com.pulse.android.core"

    // adding default values so that unmocked values do not throw anything
    // https://developer.android.com/training/testing/local-tests?utm_source=android-studio-app&utm_medium=app&utm_content=ui#error
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(projects.instrumentation.interaction.interactionRemote)
    implementation(projects.pulseUtils)
    testImplementation(testFixtures(projects.instrumentation.interaction.interactionRemote))
    implementation(libs.okhttp)
    implementation(libs.kotlin.serialisation)
    implementation(libs.kotlin.coroutines)
    testImplementation(project(":test-common"))
    testImplementation(project(":session"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlin.coroutinesTest)
    androidTestImplementation(libs.kotlin.coroutinesTest)
}
