package com.pulse.extensions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class SourceMapsExtension @Inject constructor(
    @Suppress("UnusedPrivateProperty") objects: ObjectFactory
) {
    abstract val apiUrl: Property<String>
    abstract val mappingFile: RegularFileProperty
    abstract val appVersion: Property<String>
    abstract val versionCode: Property<Int>
}

