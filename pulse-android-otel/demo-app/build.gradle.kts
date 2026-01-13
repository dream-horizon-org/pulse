import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(rootLibs.plugins.androidApp)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    id("pulse.plugin")
}

val localProperties = Properties()
localProperties.load(FileInputStream(rootProject.file("local.properties")))

android {
    namespace = "io.opentelemetry.android.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.opentelemetry.android.demo"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        all {
            val accessToken = localProperties["rum.access.token"] as String?
            resValue("string", "rum_access_token", accessToken ?: "fakebroken")
            manifestPlaceholders["appName"] = "OpenTelemetry Android Demo"
            manifestPlaceholders["appNameSuffix"] = "default"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["debug"]
        }
    }
    buildFeatures {
        viewBinding = true
    }
    val javaVersion = JavaVersion.VERSION_11
    compileOptions {
        sourceCompatibility(javaVersion)
        targetCompatibility(javaVersion)
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.opentelemetry.api.incubator)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material.icons.core)

    coreLibraryDesugaring(libs.desugarJdkLibs)

    // These are sourced from local project dirs. See settings.gradle.kts for the
    // configured substitutions.
    implementation(libs.pulse.android.sdk)    //parent dir
    implementation(libs.pulse.instrumentation.compose.click)
    implementation(libs.pulse.instrumentation.sessions)
    implementation(libs.pulse.instrumentation.activity)
    implementation(libs.pulse.instrumentation.fragment)
    implementation(libs.pulse.instrumentation.view.click)
    implementation(libs.pulse.instrumentation.slowrendering)
    implementation(libs.pulse.instrumentation.locationLibrary)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.opentelemetry.exporter.otlp)

    testImplementation(libs.bundles.junit)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "com.squareup.okhttp3" && requested.name == "okhttp-jvm") {
                useTarget("com.squareup.okhttp3:okhttp:${requested.version}")
                because("choosing okhttp over okhttp-jvm")
            }
        }
    }
}

// Configure Pulse plugin extension
pulse {
    sourcemaps {
        apiUrl.set("http://localhost:8080/v1/symbolicate/file/upload")
        mappingFile.set(file("/tmp/test-upload/mapping.txt"))
        appVersion.set("0.0.1")
        versionCode.set(123)
    }
}

