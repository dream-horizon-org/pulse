@file:Suppress("UnstableApiUsage")

rootProject.name = "opentelemetry-android-demo"

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
        google()
    }
}

includeBuild("../pulse-upload-plugin")
includeBuild("..") {
    dependencySubstitution {
        substitute(module("io.opentelemetry.android:android-agent"))
            .using(project(":android-agent"))
        substitute(module("io.opentelemetry.android.instrumentation:compose-click"))
            .using(project(":instrumentation:compose:click"))
        substitute(module("io.opentelemetry.android.instrumentation:sessions"))
            .using(project(":instrumentation:sessions"))
        substitute(module("io.opentelemetry.android.instrumentation:activity"))
            .using(project(":instrumentation:activity"))
        substitute(module("io.opentelemetry.android.instrumentation:fragment"))
            .using(project(":instrumentation:fragment"))
        substitute(module("io.opentelemetry.android.instrumentation:view-click"))
            .using(project(":instrumentation:view-click"))
        substitute(module("io.opentelemetry.android.instrumentation:slowrendering"))
            .using(project(":instrumentation:slowrendering"))
        // TODO update with final coordinates
        substitute(module("io.opentelemetry.android.instrumentation:interaction-library"))
            .using(project(":instrumentation:interaction:interaction-library"))
        substitute(module("io.opentelemetry.android.instrumentation:interaction-remote"))
            .using(project(":instrumentation:interaction:interaction-remote"))
        substitute(module("io.opentelemetry.android.instrumentation:interaction-core"))
            .using(project(":instrumentation:interaction:interaction-core"))
        substitute(module("io.opentelemetry.android:pulse-android-sdk"))
            .using(project(":pulse-android-sdk"))
    }
}