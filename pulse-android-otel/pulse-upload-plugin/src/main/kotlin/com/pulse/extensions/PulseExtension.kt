package com.pulse.extensions

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class PulseExtension @Inject constructor(
    objects: ObjectFactory
) {
    private val _sourcemaps: PulseSourceMapsExtension = objects.newInstance(PulseSourceMapsExtension::class.java)
    private val _symbols: PulseNativeSymbolsExtension = objects.newInstance(PulseNativeSymbolsExtension::class.java)

    val sourcemaps: PulseSourceMapsExtension
        get() = _sourcemaps

    val symbols: PulseNativeSymbolsExtension
        get() = _symbols

    fun sourcemaps(action: Action<PulseSourceMapsExtension>) {
        action.execute(_sourcemaps)
    }

    fun symbols(action: Action<PulseNativeSymbolsExtension>) {
        action.execute(_symbols)
    }
}
