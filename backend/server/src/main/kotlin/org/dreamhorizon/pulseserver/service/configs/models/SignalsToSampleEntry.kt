package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Per-rule signal sampling for a session (matches mobile SDK `sampling.signalsToSample` JSON).
 */
data class SignalsToSampleEntry(
    @get:JsonProperty("condition")
    @param:JsonProperty("condition")
    val condition: EventFilter? = null,
    @get:JsonProperty("sampleRate")
    @param:JsonProperty("sampleRate")
    val sampleRate: Double = 0.0,
)
