package com.pulse.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.pulse.extensions.PulseExtension
import com.pulse.tasks.UploadSourceMapsTask

abstract class PulsePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("pulse", PulseExtension::class.java)

        project.tasks.register("uploadSourceMaps", UploadSourceMapsTask::class.java)

        project.afterEvaluate {
            val task = project.tasks.named("uploadSourceMaps", UploadSourceMapsTask::class.java).get()
            task.group = "pulse"
            // Command-line options will override these if provided
            task.apiUrl.convention(extension.sourcemaps.apiUrl)
            task.mappingFile.convention(extension.sourcemaps.mappingFile)
            task.appVersion.convention(extension.sourcemaps.appVersion)
            task.versionCode.convention(extension.sourcemaps.versionCode)
        }
    }
}

