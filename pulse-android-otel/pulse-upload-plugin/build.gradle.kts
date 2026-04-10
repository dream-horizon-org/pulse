import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
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

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required.set(true)
        checkstyle.required.set(false)
    }
}

dependencies {
    detektPlugins(libs.detekt.rules.libraries)
    implementation(libs.android.plugin)
    implementation(libs.gson)
}
