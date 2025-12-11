plugins {
    `kotlin-dsl`
}

group = "com.pulse"
version = "0.0.1"

gradlePlugin {
    plugins {
        create("pulsePlugin") {
            id = "pulse.plugin"
            implementationClass = "com.pulse.plugins.PulsePlugin"
            displayName = "Pulse Gradle Plugin"
            description = "Pulse Gradle plugin for uploading build artifacts"
        }
    }
}

dependencies {
    implementation(libs.android.plugin)
    implementation(libs.gson)
}
