plugins {
    `kotlin-dsl`
}

group = "com.pulse"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("pulseUploadSourceMaps") {
            id = "pulse.upload-sourcemaps"
            implementationClass = "com.pulse.plugins.PulsePlugin"
            displayName = "Pulse Source Maps Upload Plugin"
            description = "Upload ProGuard/R8 mapping files to Pulse backend"
        }
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("com.google.code.gson:gson:2.13.2")
}
