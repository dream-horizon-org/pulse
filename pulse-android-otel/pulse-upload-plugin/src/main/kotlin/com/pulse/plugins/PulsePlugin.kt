package com.pulse.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.pulse.tasks.UploadSourceMapsTask

class PulsePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("uploadSourceMaps", UploadSourceMapsTask::class.java) {
            group = "pulse"
        }
    }
}

