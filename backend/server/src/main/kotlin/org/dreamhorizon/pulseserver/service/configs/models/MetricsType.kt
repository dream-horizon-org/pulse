package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Polymorphic metric instrument for [MetricsToAddEntry]; JSON discriminator is `"type"`.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = MetricsType.Counter::class, name = "counter"),
    JsonSubTypes.Type(value = MetricsType.Gauge::class, name = "gauge"),
    JsonSubTypes.Type(value = MetricsType.Histogram::class, name = "histogram"),
    JsonSubTypes.Type(value = MetricsType.Sum::class, name = "sum"),
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class MetricsType {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Counter(
        var type: String? = null,
    ) : MetricsType()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Gauge(
        var type: String? = null,
        @get:JsonProperty("isFraction")
        var isFraction: Boolean = false,
    ) : MetricsType()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Histogram(
        var type: String? = null,
        var bucket: List<Double>? = null,
        @get:JsonProperty("isFraction")
        var isFraction: Boolean = false,
    ) : MetricsType()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Sum(
        var type: String? = null,
        @get:JsonProperty("isFraction")
        var isFraction: Boolean = false,
        @get:JsonProperty("isMonotonic")
        var isMonotonic: Boolean = false,
    ) : MetricsType()
}
