package com.pulse.plugins

import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.pulse.extensions.PulseExtension
import com.pulse.tasks.PulseUploadSourceMapsTask

class PulsePlugin : Plugin<Project> {
    companion object {
        private const val PULSE_NAME = "pulse"
        const val PULSE_EXTENSION = PULSE_NAME
        const val TASK_GROUP = PULSE_NAME

        private const val UPLOAD_TASK = "uploadSourceMaps"
        private val TASK_CLASS = PulseUploadSourceMapsTask::class.java
    }

    override fun apply(project: Project) {
        if (project.plugins.hasPlugin(AppPlugin::class.java)) {
            val extension = project.extensions.create(PULSE_EXTENSION, PulseExtension::class.java)

            project.tasks.register(UPLOAD_TASK, TASK_CLASS)

            project.afterEvaluate {
                val task = project.tasks.named(UPLOAD_TASK, TASK_CLASS).get()
                // Command-line options will override these if provided
                task.apiUrl.convention(extension.sourcemaps.apiUrl)
                task.mappingFile.convention(extension.sourcemaps.mappingFile)
                task.appVersion.convention(extension.sourcemaps.appVersion)
                task.versionCode.convention(extension.sourcemaps.versionCode)
            }
        }
    }
}

