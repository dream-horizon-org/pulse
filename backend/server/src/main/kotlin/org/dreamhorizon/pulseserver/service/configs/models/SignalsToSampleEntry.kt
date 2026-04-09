package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Per-rule signal sampling for a session (matches mobile SDK `sampling.signalsToSample` JSON).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SignalsToSampleEntry(
    @JsonProperty("condition")
    var condition: EventFilter? = null,
    @JsonProperty("sampleRate")
    var sampleRate: Double = 0.0,
)
