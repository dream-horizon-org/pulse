package com.pulse.semconv

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeKey.stringKey

public object PulseAttributes {
    /**
     * Type of signal. For example, `crash`, `arn`, `interaction`. See [PulseTypeValues]
     */
    @JvmField
    public val PULSE_TYPE: AttributeKey<String> = stringKey("pulse.type")

    @JvmField
    public val PULSE_NAME: AttributeKey<String> = stringKey("pulse.name")

    @JvmField
    public val PULSE_SPAN_ID: AttributeKey<String> = stringKey("pulse.span.id")

    public object PulseTypeValues {
        public const val CUSTOM_EVENT: String = "custom_event"
        public const val ANR: String = "device.anr"
        public const val CRASH: String = "device.crash"
        public const val TOUCH: String = "app.click"
        public const val APP_START: String = "app_start"
        public const val SCREEN_SESSION: String = "screen_session"
        public const val SCREEN_LOAD: String = "screen_load"
        public const val FROZEN: String = "app.jank.frozen"
        public const val SLOW: String = "app.jank.slow"
        public const val NON_FATAL: String = "non_fatal"
        public const val INTERACTION: String = "interaction"
        public const val NETWORK: String = "network"
        public const val NETWORK_CHANGE: String = "network.change"
    }
}
