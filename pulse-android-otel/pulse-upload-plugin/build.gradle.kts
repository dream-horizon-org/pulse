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
    
    // Suppress pre-existing issues on a per-project basis
    val baselineFile = rootProject.file("../config/detekt/baseline.xml")
    if (baselineFile.exists()) {
        baseline = baselineFile
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
    }
}

tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektDebug") {
    description = "Run detekt analysis for debug source sets"
    group = "verification"
    
    setSource(files("src/main/kotlin"))
    val detektConfig = rootProject.file("../config/detekt/detekt.yml")
    if (detektConfig.exists()) {
        config.setFrom(files(detektConfig))
    }
    val baselineFile = rootProject.file("../config/detekt/baseline.xml")
    if (baselineFile.exists()) {
        baseline.set(baselineFile)
    }
    
    buildUponDefaultConfig = true
    autoCorrect = true
    
    debug = true
}

dependencies {
    implementation(libs.android.plugin)
    implementation(libs.gson)
}
