package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * One derived-metric rule under `signals.metricsToAdd`.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MetricsToAddEntry(
    @JsonProperty("name")
    var name: String? = null,
    @JsonProperty("target")
    var target: MetricsToAddTarget? = null,
    @JsonProperty("condition")
    var condition: EventFilter? = null,
    @JsonProperty("type")
    var type: MetricsType? = null,
    @JsonProperty("attributesToPick")
    var attributesToPick: List<EventFilter>? = null,
)
