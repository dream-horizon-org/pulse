@file:Suppress("DEPRECATION")

package com.pulse.android.api.otel.models

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.sdk.logs.data.Body
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.resources.Resource

/**
 * [LogRecordData] data with all parameters as properties which can be changed using [copy].
 */
public class PulseLogRecordData(
    private val delegate: LogRecordData,
    private val resource: Resource,
    private val eventName: String?,
    private val bodyValue: Value<*>?,
    private val attributes: Attributes,
    private val severity: Severity,
    private val severityText: String?,
) : LogRecordData by delegate,
    PulseSignalData {
    override fun getResource(): Resource = resource

    override fun getAttributes(): Attributes = attributes

    override fun getSeverity(): Severity = severity

    override fun getSeverityText(): String? = severityText

    @Deprecated("Use getBodyValue instead")
    override fun getBody(): Body = if (bodyValue == null) Body.empty() else Body.string(bodyValue.asString())

    override fun getBodyValue(): Value<*>? = bodyValue

    override fun getTotalAttributeCount(): Int = getAttributes().size()

    override fun getEventName(): String? = eventName

    @Suppress("NullableToStringCall") // null is mapped to "null"
    override fun toString(): String =
        "PulseLogRecordData(resource=$resource, attributes=${getAttributes()}, bodyValue=$bodyValue, eventName=$eventName, " +
            "severity=$severity, severityText=$severityText)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PulseLogRecordData) return false
        return delegate == other.delegate &&
            resource == other.resource &&
            eventName == other.eventName &&
            bodyValue == other.bodyValue &&
            getAttributes() == other.getAttributes() &&
            severity == other.severity &&
            severityText == other.severityText
    }

    override fun hashCode(): Int {
        var result = resource.hashCode()
        result = 31 * result + (eventName?.hashCode() ?: 0)
        result = 31 * result + (bodyValue?.hashCode() ?: 0)
        result = 31 * result + getAttributes().hashCode()
        result = 31 * result + severity.hashCode()
        result = 31 * result + (severityText?.hashCode() ?: 0)
        return result
    }
}

/**
 * Creates a new instance of [PulseLogRecordData].
 * All params default to the corresponding value from receiver.
 */
public fun LogRecordData.copy(
    resource: Resource = this.resource,
    severity: Severity = this.severity,
    severityText: String? = this.severityText,
    bodyValue: Value<*>? = this.bodyValue,
    attributes: Attributes = this.attributes,
    eventName: String? = this.eventName,
): PulseLogRecordData =
    PulseLogRecordData(
        delegate = this,
        resource = resource,
        severity = severity,
        severityText = severityText,
        bodyValue = bodyValue,
        attributes = attributes,
        eventName = eventName,
    )
