@file:Suppress("UnstableApiUsage")

plugins {
    id(libs.plugins.androidFusedLibrary.get().pluginId)
    `maven-publish`
}

description = "Pulse Android SDK Fused - Fused library for non-published android modules"

val minSdkValue = (property("android.minSdk") as String).toInt()
androidFusedLibrary {
    namespace = "com.pulse.android.sdk.fused"
    minSdk = minSdkValue

    aarMetadata {
        minCompileSdk = minSdkValue
    }
}

dependencies {
    include(projects.pulseAndroidSdk)
    include(projects.pulseSamplingModels)
    include(projects.pulseSamplingCore)
    include(projects.pulseSamplingRemote)

    // External dependencies needed to build the fused library
    // These will be declared as dependencies in the POM
//    implementation(projects.pulseUtils)
//    implementation(platform(libs.opentelemetry.platform.alpha))
//    implementation(libs.opentelemetry.api)
//    implementation(libs.opentelemetry.sdk)
//    implementation(libs.opentelemetry.semconv.incubating)
//    implementation(libs.kotlin.serialisation)
//    implementation(libs.kotlin.coroutines)
//    implementation(libs.okhttp)
//    implementation(libs.retrofit)
//    implementation(libs.retrofit.kotlinSerialisation)
}

// see https://developer.android.com/build/publish-library/fused-library
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "pulse-android-sdk"
            version = project.version.toString()

            // Use fusedLibraryComponent instead of regular component
            // This automatically handles dependency exclusion
            from(components["fusedLibraryComponent"])

            pom {
                val repoUrl = "https://github.com/dream-horizon-org/pulse"
                name.set("Pulse Android SDK")
                description.set("Pulse SDK for mobile observability")
                url.set(repoUrl)
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    val scmUrl = "scm:git:git@github.com:dream-horizon-org/pulse.git"
                    connection.set(scmUrl)
                    developerConnection.set(scmUrl)
                    url.set(repoUrl)
                    tag.set("HEAD")
                }
                developers {
                    developer {
                        id.set("dreamhorizon")
                        name.set("DreamHorizon")
                        url.set("https://dreamhorizon.org")
                    }
                }
            }
        }
    }
}

// Apply versioning conventions (same as other modules)
if (findProperty("otel.stable") != "true") {
    version = "$version-alpha"
}
if (findProperty("final") != "true") {
    version = "$version-SNAPSHOT"
}
