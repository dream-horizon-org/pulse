plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
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

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    val detektConfig = rootProject.file("../config/detekt/detekt.yml")
    if (detektConfig.exists()) {
        config.from(detektConfig)
    }
}

dependencies {
    implementation(libs.android.plugin)
    implementation(libs.gson)
}
