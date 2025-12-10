plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
}

group = "com.pulse"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("pulseUploadSourceMaps") {
            id = "pulse.upload-sourcemaps"
            implementationClass = "com.pulse.upload.PulseUploadSourceMapsPlugin"
            displayName = "Pulse Source Maps Upload Plugin"
            description = "Upload ProGuard/R8 mapping files to Pulse backend"
        }
    }
}

