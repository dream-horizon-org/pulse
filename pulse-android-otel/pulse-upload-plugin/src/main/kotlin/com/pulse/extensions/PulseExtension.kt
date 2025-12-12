package com.pulse.extensions

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class PulseExtension @Inject constructor(
    objects: ObjectFactory
) {
    private val _sourcemaps: PulseSourceMapsExtension = objects.newInstance(PulseSourceMapsExtension::class.java)

    val sourcemaps: PulseSourceMapsExtension
        get() = _sourcemaps

    fun sourcemaps(action: Action<PulseSourceMapsExtension>) {
        action.execute(_sourcemaps)
    }
}
