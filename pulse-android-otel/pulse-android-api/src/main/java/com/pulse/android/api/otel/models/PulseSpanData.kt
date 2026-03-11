@file:Suppress("DEPRECATION")

package com.pulse.android.api.otel.models

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * [SpanData] with all parameters as properties which can be changed using [copy].
 */
public class PulseSpanData(
    private val delegate: SpanData,
    private val name: String,
    private val attributes: Attributes,
    private val events: List<EventData>,
    private val resource: Resource,
) : SpanData by delegate,
    PulseSignalData {
    override fun getName(): String = name

    override fun getAttributes(): Attributes = attributes

    override fun getTotalAttributeCount(): Int = getAttributes().size()

    override fun getEvents(): List<EventData> = events

    override fun getTotalRecordedEvents(): Int = events.size

    override fun getResource(): Resource = resource

    override fun getTraceId(): String? = delegate.traceId

    override fun getSpanId(): String? = delegate.spanId

    override fun getParentSpanId(): String? = delegate.parentSpanId

    override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo? = delegate.instrumentationScopeInfo

    override fun toString(): String = "PulseSpanData(name=$name, attributes=${getAttributes()}, events=$events, resource=$resource)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PulseSpanData) return false
        return delegate == other.delegate &&
            name == other.name &&
            getAttributes() == other.getAttributes() &&
            events == other.events &&
            resource == other.resource
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + getAttributes().hashCode()
        result = 31 * result + events.hashCode()
        result = 31 * result + resource.hashCode()
        return result
    }
}

/**
 * Creates a new instance of [PulseSpanData].
 * All params default to the corresponding value from receiver.
 */
public fun SpanData.copy(
    name: String = this.name,
    attributes: Attributes = this.attributes,
    events: List<EventData> = this.events,
    resource: Resource = this.resource,
): PulseSpanData =
    PulseSpanData(
        delegate = this,
        name = name,
        attributes = attributes,
        events = events,
        resource = resource,
    )
