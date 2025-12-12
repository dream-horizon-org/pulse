package com.pulse.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.pulse.extensions.PulseExtension
import com.pulse.tasks.UploadSourceMapsTask

abstract class PulsePlugin : Plugin<Project> {
    companion object {
        private const val TASK_NAME = "uploadSourceMaps"
        private const val PULSE_NAME = "pulse"
        private const val EXTENSION_NAME = PULSE_NAME
        private const val TASK_GROUP = PULSE_NAME
        private val TASK_CLASS = UploadSourceMapsTask::class.java
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, PulseExtension::class.java)

        project.tasks.register(TASK_NAME, TASK_CLASS)

        project.afterEvaluate {
            val task = project.tasks.named(TASK_NAME, TASK_CLASS).get()
            task.group = TASK_GROUP
            // Command-line options will override these if provided
            task.apiUrl.convention(extension.sourcemaps.apiUrl)
            task.mappingFile.convention(extension.sourcemaps.mappingFile)
            task.appVersion.convention(extension.sourcemaps.appVersion)
            task.versionCode.convention(extension.sourcemaps.versionCode)
        }
    }
}

