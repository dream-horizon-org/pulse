import org.gradle.api.artifacts.VersionCatalogsExtension

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "com.squareup.okhttp3" && requested.name == "okhttp-jvm") {
                useTarget("com.squareup.okhttp3:okhttp:${requested.version}")
                because("choosing okhttp over okhttp-jvm")
                return@eachDependency
            }

            val libsCatalog =
                rootProject.extensions
                    .findByType(VersionCatalogsExtension::class.java)
                    ?.find("libs")
                    ?.orElse(null)
                    ?: return@eachDependency

            val kotlinVer =
                libsCatalog.findVersion("kotlin").map { it.requiredVersion }.orElse(null)
                    ?: return@eachDependency
            val coroutinesVer =
                libsCatalog.findVersion("kotlinCoroutines").map { it.requiredVersion }.orElse("1.8.1")
            val serializationVer =
                libsCatalog.findVersion("kotlinSerialisation").map { it.requiredVersion }.orElse("1.6.3")

            when {
                requested.group == "org.jetbrains.kotlin" -> {
                    useVersion(kotlinVer)
                    because("Align Kotlin artifacts with compiler ($kotlinVer); block transitive Kotlin 2.x stdlib under Kotlin 1.9")
                }
                requested.group == "org.jetbrains.kotlinx" &&
                    requested.name.contains("kotlinx-coroutines") -> {
                    useVersion(coroutinesVer)
                    because("Align kotlinx-coroutines with Kotlin $kotlinVer toolchain")
                }
                requested.group == "org.jetbrains.kotlinx" &&
                    requested.name.contains("kotlinx-serialization") -> {
                    useVersion(serializationVer)
                    because("Align kotlinx-serialization with Kotlin $kotlinVer toolchain")
                }
            }
        }
    }
}
