package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * One derived-metric rule under `signals.metricsToAdd`.
 */
data class MetricsToAddEntry(
    @param:JsonProperty("name")
    @get:JsonProperty("name")
    var name: String? = null,
    @param:JsonProperty("target")
    @get:JsonProperty("target")
    var target: MetricsToAddTarget? = null,
    @param:JsonProperty("condition")
    @get:JsonProperty("condition")
    var condition: EventFilter? = null,
    @param:JsonProperty("type")
    @get:JsonProperty("type")
    var type: MetricsType? = null,
    @param:JsonProperty("attributesToPick")
    @get:JsonProperty("attributesToPick")
    var attributesToPick: List<EventFilter>? = null,
)
