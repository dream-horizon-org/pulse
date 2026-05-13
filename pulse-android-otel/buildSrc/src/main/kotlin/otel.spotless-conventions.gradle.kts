import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
}

val ktlintToolVersion =
        rootProject.extensions.getByType(VersionCatalogsExtension::class.java).named("libs").findVersion("ktlint").get().requiredVersion

spotless {
    java {
        googleJavaFormat().aosp()
        licenseHeaderFile(rootProject.file("gradle/spotless.license.java"), "(package|import|public)")
        target("src/**/*.java")
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configureKotlin(this@spotless)
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        configureKotlin(this@spotless)
    }
    kotlinGradle {
        ktlint(ktlintToolVersion)
    }
    format("misc") {
        // not using "**/..." to help keep spotless fast
        target(
                ".gitignore",
                ".gitattributes",
                ".gitconfig",
                ".editorconfig",
                "*.md",
                "src/**/*.md",
                "docs/**/*.md",
                "*.sh",
                "src/**/*.properties"
        )
        leadingTabsToSpaces()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Use root declared tool deps to avoid issues with high concurrency.
// see https://github.com/diffplug/spotless/tree/main/plugin-gradle#dependency-resolution-modes
if (project == rootProject) {
    spotless {
        predeclareDeps()
    }
    with(extensions["spotlessPredeclare"] as SpotlessExtension) {
        java {
            googleJavaFormat()
        }
        kotlin {
            ktlint(ktlintToolVersion)
        }
        kotlinGradle {
            ktlint(ktlintToolVersion)
        }
    }
}

fun configureKotlin(
    spotlessExtension: SpotlessExtension,
) {
    spotlessExtension.kotlin {
        ktlint(ktlintToolVersion)
        target("src/**/*.kt")
    }
}