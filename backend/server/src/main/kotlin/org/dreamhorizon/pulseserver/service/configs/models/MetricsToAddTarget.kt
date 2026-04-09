package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Polymorphic `target` for [MetricsToAddEntry]; JSON discriminator is `"type"`
 * (`name`, `attribute`).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = MetricsToAddTarget.Name::class, name = "name"),
    JsonSubTypes.Type(value = MetricsToAddTarget.Attribute::class, name = "attribute"),
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class MetricsToAddTarget {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Name(
        var type: String? = null,
    ) : MetricsToAddTarget()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Attribute(
        var type: String? = null,
        var condition: EventFilter? = null,
        @get:JsonProperty("shouldAddPropNameAsSuffix")
        var shouldAddPropNameAsSuffix: Boolean = false,
    ) : MetricsToAddTarget()
}
