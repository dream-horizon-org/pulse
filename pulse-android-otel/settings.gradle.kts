rootProject.name = "opentelemetry-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("com.gradle.develocity") version "4.2.2"
}

develocity {
    buildScan {
        publishing.onlyIf { System.getenv("CI") != null }
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}

include(":core")
include(":android-agent")
include(":test-common")
include(":animal-sniffer-signature")
include(":common")
include(":services")
include(":session")
include(":opentelemetry-android-bom")
include(":pulse-android-sdk")
include(":pulse-semconv")
include(":pulse-utils")
include(":pulse-sampling-models")
project(":pulse-sampling-models").projectDir = File("./pulse-sampling/models")
include(":pulse-sampling-core")
project(":pulse-sampling-core").projectDir = File("./pulse-sampling/core")
include(":pulse-sampling-otel")
project(":pulse-sampling-otel").projectDir = File("./pulse-sampling/otel")
includeBuild("pulse-upload-plugin")
includeFromDir("instrumentation") {
    if (it.contains(":instrumentation:interaction:")) {
        val nameWithInteraction = it.split(":").takeLast(2).joinToString("-")
        println("Name for projectPath = $it is $nameWithInteraction")
        nameWithInteraction
    } else {
        it.substringAfterLast(":")
    }
}

fun includeFromDir(
    dirName: String,
    maxDepth: Int = 3,
    nameProvider: (projectPath: String) -> String = { it.substringAfterLast(":") }
) {
    val instrumentationDir = File(rootDir, dirName)
    val separator = Regex("[/\\\\]")
    instrumentationDir.walk().maxDepth(maxDepth).forEach {
        if (it.name.equals("build.gradle.kts")) {
            val projectPath = ":$dirName:${
                it.parentFile.toRelativeString(instrumentationDir).replace(separator, ":")
            }"
            println("Including projectPath = $projectPath")
            include(projectPath)
            project(projectPath).name = nameProvider(projectPath)
        }
    }
}
