package com.pulse.sampling.core.providers

import com.pulse.sampling.models.PulseSdkConfig

public fun interface PulseSdkConfigProvider {
    /**
     * Provides the [com.pulse.sampling.models.PulseSdkConfig] which governs the sdk behaviour
     */
    public suspend fun provide(): PulseSdkConfig?
}
