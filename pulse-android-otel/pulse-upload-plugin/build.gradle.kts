import org.gradle.api.GradleException

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

    val detektConfigPath = rootProject.file("../config/detekt/detekt.yml")
    if (detektConfigPath.exists()) {
        config.from(detektConfigPath)
    } else {
        throw GradleException("Detekt config is not found for path $detektConfigPath")
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
    }
}

dependencies {
    implementation(libs.android.plugin)
    implementation(libs.gson)
}
