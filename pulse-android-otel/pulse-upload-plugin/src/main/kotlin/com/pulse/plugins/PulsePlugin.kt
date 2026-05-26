package com.pulse.plugins

import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.pulse.extensions.PulseExtension
import com.pulse.tasks.PulseSymbolUploadKind
import com.pulse.tasks.PulseUploadSourceMapsTask

class PulsePlugin : Plugin<Project> {
    companion object {
        private const val PULSE_NAME = "pulse"
        const val PULSE_EXTENSION = PULSE_NAME
        const val TASK_GROUP = PULSE_NAME

        private const val UPLOAD_SOURCE_MAPS_TASK = "uploadSourceMaps"
        private const val UPLOAD_SYMBOL_FILES_TASK = "uploadSymbolFiles"
        private val UPLOAD_TASK_CLASS = PulseUploadSourceMapsTask::class.java
    }

    override fun apply(project: Project) {
        if (project.plugins.hasPlugin(AppPlugin::class.java)) {
            val extension = project.extensions.create(PULSE_EXTENSION, PulseExtension::class.java)

            project.tasks.register(UPLOAD_SOURCE_MAPS_TASK, UPLOAD_TASK_CLASS) {
                uploadKind.set(PulseSymbolUploadKind.MAPPING)
            }
            project.tasks.register(UPLOAD_SYMBOL_FILES_TASK, UPLOAD_TASK_CLASS) {
                uploadKind.set(PulseSymbolUploadKind.NDK)
            }

            project.afterEvaluate {
                val sourceMapsTask = project.tasks.named(UPLOAD_SOURCE_MAPS_TASK, UPLOAD_TASK_CLASS).get()
                sourceMapsTask.apiUrl.convention(extension.sourcemaps.apiUrl)
                sourceMapsTask.mappingFile.convention(extension.sourcemaps.mappingFile)
                sourceMapsTask.appVersion.convention(extension.sourcemaps.appVersion)
                sourceMapsTask.versionCode.convention(extension.sourcemaps.versionCode)
                sourceMapsTask.apiKey.convention(extension.sourcemaps.apiKey)

                val symbolFilesTask = project.tasks.named(UPLOAD_SYMBOL_FILES_TASK, UPLOAD_TASK_CLASS).get()
                symbolFilesTask.apiUrl.convention(extension.symbols.apiUrl)
                symbolFilesTask.appVersion.convention(extension.symbols.appVersion)
                symbolFilesTask.versionCode.convention(extension.symbols.versionCode)
                symbolFilesTask.apiKey.convention(extension.symbols.apiKey)
                symbolFilesTask.objFile.convention(extension.symbols.objFile)

                project.tasks.matching { it.name.startsWith("externalNativeBuild") }
                    .configureEach { symbolFilesTask.dependsOn(this) }
            }
        }
    }
}
