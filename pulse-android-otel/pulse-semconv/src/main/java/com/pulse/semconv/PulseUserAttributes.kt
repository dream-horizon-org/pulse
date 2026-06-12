package com.pulse.semconv

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.semconv.AttributeKeyTemplate
import io.opentelemetry.semconv.AttributeKeyTemplate.stringKeyTemplate

public object PulseUserAttributes {
    /**
     * All the custom user props will be mapped with this template
     */
    @JvmField
    public val PULSE_USER_PARAMETER: AttributeKeyTemplate<String> = stringKeyTemplate("pulse.user")

    /**
     * The previous user.id for this user, when known.
     */
    @JvmField
    public val PULSE_USER_PREVIOUS_ID: AttributeKey<String> = stringKey("pulse.user.previous_id")

    public const val PULSE_USER_SESSION_START_EVENT_NAME: String = "pulse.user.session.start"
    public const val PULSE_USER_SESSION_END_EVENT_NAME: String = "pulse.user.session.end"
}
