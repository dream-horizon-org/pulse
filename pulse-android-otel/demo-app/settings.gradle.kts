@file:Suppress("UnstableApiUsage")

rootProject.name = "pulse-android-demo"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    versionCatalogs {
        create("rootLibs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
        google()
    }
}

includeBuild("../pulse-upload-plugin")
// Removed includeBuild for parent project - now using Maven dependencies from local repository
