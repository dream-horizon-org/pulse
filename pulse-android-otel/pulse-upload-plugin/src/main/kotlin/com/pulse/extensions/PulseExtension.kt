package com.pulse.extensions

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class PulseExtension @Inject constructor(
    objects: ObjectFactory
) {
    private val _sourcemaps: SourceMapsExtension = objects.newInstance(SourceMapsExtension::class.java)

    val sourcemaps: SourceMapsExtension
        get() = _sourcemaps

    fun sourcemaps(action: Action<SourceMapsExtension>) {
        action.execute(_sourcemaps)
    }
}
