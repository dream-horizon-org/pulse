package org.dreamhorizon.pulseserver.service.configs.models

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
sealed class MetricsToAddTarget {

    data class Name(
        @get:JsonProperty("type")
        @param:JsonProperty("type")
        val type: String? = null,
    ) : MetricsToAddTarget()

    data class Attribute(
        @get:JsonProperty("type")
        @param:JsonProperty("type")
        val type: String? = null,
        @get:JsonProperty("condition")
        @param:JsonProperty("condition")
        val condition: EventFilter? = null,
        @get:JsonProperty("shouldAddPropNameAsSuffix")
        @param:JsonProperty("shouldAddPropNameAsSuffix")
        val shouldAddPropNameAsSuffix: Boolean = false,
    ) : MetricsToAddTarget()
}
