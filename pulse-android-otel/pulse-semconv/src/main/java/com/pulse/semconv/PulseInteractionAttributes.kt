package com.pulse.semconv

import com.pulse.android.core.InteractionConstant
import io.opentelemetry.api.common.AttributeKey

public object PulseInteractionAttributes {
    /**
     * Type of signal. For example, `crash`, `arn`, `interaction`
     */
    public val PULSE_TYPE: AttributeKey<String> = AttributeKey.stringKey("pulse.type")

    public val INTERACTION_NAMES: AttributeKey<List<String>> =
        AttributeKey.stringArrayKey("pulse.interaction.names")
    public val INTERACTION_IDS: AttributeKey<List<String>> =
        AttributeKey.stringArrayKey("pulse.interaction.ids")
    public val INTERACTION_NAME: AttributeKey<String> =
        AttributeKey.stringKey(InteractionConstant.NAME)
    public val INTERACTION_ID: AttributeKey<String> = AttributeKey.stringKey(InteractionConstant.ID)
    public val INTERACTION_CONFIG_ID: AttributeKey<String> = AttributeKey.stringKey(
        InteractionConstant.CONFIG_ID
    )
    public val INTERACTION_LAST_EVENT_TIME: AttributeKey<Long> =
        AttributeKey.longKey(InteractionConstant.LAST_EVENT_TIME_IN_NANO)
}