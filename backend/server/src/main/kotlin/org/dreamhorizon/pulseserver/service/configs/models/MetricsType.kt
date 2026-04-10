package org.dreamhorizon.pulseserver.service.configs.models

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
sealed class MetricsType {

    data class Counter(
        @get:JsonProperty("type")
        @param:JsonProperty("type")
        val type: String? = null,
    ) : MetricsType()

    data class Gauge(
        @get:JsonProperty("type")
        @param:JsonProperty("type")
        val type: String? = null,
        @get:JsonProperty("isFraction")
        @param:JsonProperty("isFraction")
        val isFraction: Boolean = false,
    ) : MetricsType()

    data class Histogram(
        @get:JsonProperty("type")
        @param:JsonProperty("type")
        val type: String? = null,
        @get:JsonProperty("bucket")
        @param:JsonProperty("bucket")
        val bucket: List<Double>? = null,
        @get:JsonProperty("isFraction")
        @param:JsonProperty("isFraction")
        val isFraction: Boolean = false,
    ) : MetricsType()

    data class Sum(
        @get:JsonProperty("type")
        @param:JsonProperty("type")
        val type: String? = null,
        @get:JsonProperty("isFraction")
        @param:JsonProperty("isFraction")
        val isFraction: Boolean = false,
        @get:JsonProperty("isMonotonic")
        @param:JsonProperty("isMonotonic")
        val isMonotonic: Boolean = false,
    ) : MetricsType()
}
