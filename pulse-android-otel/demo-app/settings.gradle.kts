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
includeBuild("..") {
    dependencySubstitution {
        substitute(module("org.dreamhorizon:android-agent"))
            .using(project(":android-agent"))
        substitute(module("org.dreamhorizon.instrumentation:compose-click"))
            .using(project(":instrumentation:compose:click"))
        substitute(module("org.dreamhorizon:session"))
            .using(project(":session"))
        substitute(module("org.dreamhorizon.instrumentation:activity"))
            .using(project(":instrumentation:activity"))
        substitute(module("org.dreamhorizon.instrumentation:fragment"))
            .using(project(":instrumentation:fragment"))
        substitute(module("org.dreamhorizon.instrumentation:view-click"))
            .using(project(":instrumentation:view-click"))
        substitute(module("org.dreamhorizon.instrumentation:slowrendering"))
            .using(project(":instrumentation:slowrendering"))
        substitute(module("org.dreamhorizon:interaction-interaction-library"))
            .using(project(":instrumentation:interaction:interaction-library"))
        substitute(module("org.dreamhorizon:interaction-interaction-remote"))
            .using(project(":instrumentation:interaction:interaction-remote"))
        substitute(module("org.dreamhorizon:interaction-interaction-core"))
            .using(project(":instrumentation:interaction:interaction-core"))
        substitute(module("org.dreamhorizon.instrumentation:location-location-library"))
            .using(project(":instrumentation:location:location-library"))
        substitute(module("org.dreamhorizon.instrumentation:location-location-core"))
            .using(project(":instrumentation:location:location-core"))
        substitute(module("org.dreamhorizon.instrumentation:location-location-processors"))
            .using(project(":instrumentation:location:location-processors"))
        substitute(module("org.dreamhorizon.instrumentation:location-location-models"))
            .using(project(":instrumentation:location:location-models"))
        substitute(module("org.dreamhorizon:pulse-android-sdk"))
            .using(project(":pulse-android-sdk"))
    }
}
