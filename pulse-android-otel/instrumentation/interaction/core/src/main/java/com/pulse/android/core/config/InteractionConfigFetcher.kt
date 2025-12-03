package com.pulse.android.core.config

import com.pulse.android.remote.models.InteractionConfig

public fun interface InteractionConfigFetcher {
    /**
     * Returns the list of [InteractionConfig] which should be tracked for interactions
     * In case of error returns null
     */
    public suspend fun getConfigs(): List<InteractionConfig>?
}
