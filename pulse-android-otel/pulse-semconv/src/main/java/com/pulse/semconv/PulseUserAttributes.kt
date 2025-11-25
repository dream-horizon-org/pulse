package com.pulse.semconv

import io.opentelemetry.semconv.AttributeKeyTemplate
import io.opentelemetry.semconv.AttributeKeyTemplate.stringKeyTemplate

public object PulseUserAttributes {
    /**
     * All the custom user props will be mapped with this template
     */
    public val PULSE_USER_PARAMETER: AttributeKeyTemplate<String> = stringKeyTemplate("pulse.user")
}