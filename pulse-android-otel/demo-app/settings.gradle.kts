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
        substitute(module("org.dreamhorizon:compose-click"))
            .using(project(":instrumentation:compose:click"))
        substitute(module("org.dreamhorizon:sessions"))
            .using(project(":instrumentation:sessions"))
        substitute(module("org.dreamhorizon:activity"))
            .using(project(":instrumentation:activity"))
        substitute(module("org.dreamhorizon:fragment"))
            .using(project(":instrumentation:fragment"))
        substitute(module("org.dreamhorizon:view-click"))
            .using(project(":instrumentation:view-click"))
        substitute(module("org.dreamhorizon:slowrendering"))
            .using(project(":instrumentation:slowrendering"))
        substitute(module("org.dreamhorizon:interaction-library"))
            .using(project(":instrumentation:interaction:interaction-library"))
        substitute(module("org.dreamhorizon:interaction-remote"))
            .using(project(":instrumentation:interaction:interaction-remote"))
        substitute(module("org.dreamhorizon:interaction-core"))
            .using(project(":instrumentation:interaction:interaction-core"))
        substitute(module("org.dreamhorizon:pulse-android-sdk"))
            .using(project(":pulse-android-sdk"))
    }
}
