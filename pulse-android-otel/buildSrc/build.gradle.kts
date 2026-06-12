import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.android.plugin)
    implementation(libs.spotless.plugin)
    implementation(libs.errorprone.plugin)
    implementation(libs.nullaway.plugin)
    implementation(libs.animalsniffer.plugin)
    implementation(libs.kotlin.plugin)
    implementation(libs.detekt.plugin)
    implementation(libs.binary.compat.validator)
    implementation(libs.ksp.plugin)
    implementation(libs.kover.plugin)
}
