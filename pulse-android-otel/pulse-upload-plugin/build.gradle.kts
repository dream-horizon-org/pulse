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

gradlePlugin {
    plugins {
        create("pulseUploadSourceMaps") {
            id = "pulse.upload-sourcemaps"
            implementationClass = "pulse.upload.PulseUploadSourceMapsPlugin"
            displayName = "Pulse Source Maps Upload Plugin"
            description = "Upload ProGuard/R8 mapping files to Pulse backend"
        }
    }
}

