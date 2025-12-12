package com.pulse.extensions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class PulseSourceMapsExtension {
    abstract val apiUrl: Property<String>
    abstract val mappingFile: RegularFileProperty
    abstract val appVersion: Property<String>
    abstract val versionCode: Property<Int>

    fun isConfigured(): Boolean {
        return apiUrl.isPresent &&
            mappingFile.isPresent &&
            appVersion.isPresent &&
            versionCode.isPresent
    }
}

