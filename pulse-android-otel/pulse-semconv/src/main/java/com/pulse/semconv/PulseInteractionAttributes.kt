package com.pulse.semconv

import com.pulse.android.core.InteractionConstant
import io.opentelemetry.api.common.AttributeKey

public object PulseInteractionAttributes {
    /**
     * Type of signal. For example, `crash`, `arn`, `interaction`
     */
    @JvmField
    public val PULSE_TYPE: AttributeKey<String> = AttributeKey.stringKey("pulse.type")

    @JvmField
    public val INTERACTION_NAMES: AttributeKey<List<String>> =
        AttributeKey.stringArrayKey("pulse.interaction.names")

    @JvmField
    public val INTERACTION_IDS: AttributeKey<List<String>> =
        AttributeKey.stringArrayKey("pulse.interaction.ids")

    @JvmField
    public val INTERACTION_NAME: AttributeKey<String> =
        AttributeKey.stringKey(InteractionConstant.NAME)

    @JvmField
    public val INTERACTION_ID: AttributeKey<String> = AttributeKey.stringKey(InteractionConstant.ID)

    @JvmField
    public val INTERACTION_CONFIG_ID: AttributeKey<String> =
        AttributeKey.stringKey(
            InteractionConstant.CONFIG_ID,
        )

    @JvmField
    public val INTERACTION_LAST_EVENT_TIME: AttributeKey<Long> =
        AttributeKey.longKey(InteractionConstant.LAST_EVENT_TIME_IN_NANO)
}
