package com.pulse.extensions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class PulseNativeSymbolsExtension {
    abstract val apiUrl: Property<String>
    abstract val apiKey: Property<String>
    abstract val appVersion: Property<String>
    abstract val versionCode: Property<Int>

    /** Unstripped NDK `.so` (or zip) to upload; typically under `build/intermediates/cxx/**/obj` */
    abstract val objFile: RegularFileProperty

    fun isConfigured(): Boolean {
        return apiUrl.isPresent &&
            apiKey.isPresent &&
            appVersion.isPresent &&
            versionCode.isPresent &&
            objFile.isPresent
    }
}
