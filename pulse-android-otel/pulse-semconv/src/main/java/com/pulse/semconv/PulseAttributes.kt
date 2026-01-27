package com.pulse.semconv

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.semconv.AttributeKeyTemplate
import io.opentelemetry.semconv.AttributeKeyTemplate.stringKeyTemplate
import io.opentelemetry.semconv.TelemetryAttributes

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

    @JvmField
    public val TELEMETRY_SDK_NAME_KEY: AttributeKey<String> = TelemetryAttributes.TELEMETRY_SDK_NAME

    /**
     * Tenant ID for multi-tenant applications.
     * Used in both HTTP headers (as "tenant-id") and as a global attribute (as "tenant.id").
     */
    @JvmField
    public val TENANT_ID: AttributeKey<String> = stringKey("tenant.id")

    public object PulseSdkNames {
        public const val ANDROID_JAVA: String = "pulse_android_java"
        public const val ANDROID_RN: String = "pulse_android_rn"
        public const val IOS_SWIFT: String = "pulse_ios_swift"
        public const val IOS_RN: String = "pulse_ios_rn"
    }

    public object PulseTypeValues {
        public const val CUSTOM_EVENT: String = "custom_event"
        public const val ANR: String = "device.anr"
        public const val CRASH: String = "device.crash"
        public const val TOUCH: String = "app.click"
        public const val APP_START: String = "app_start"
        public const val SCREEN_SESSION: String = "screen_session"
        public const val APP_SESSION_START: String = "session.start"
        public const val APP_SESSION_END: String = "session.end"
        public const val APP_INSTALLATION_START: String = "pulse.app.installation.start"
        public const val SCREEN_LOAD: String = "screen_load"
        public const val FROZEN: String = "app.jank.frozen"
        public const val SLOW: String = "app.jank.slow"
        public const val NON_FATAL: String = "non_fatal"
        public const val INTERACTION: String = "interaction"
        private const val NETWORK: String = "network"
        public const val NETWORK_CHANGE: String = "network.change"

        @JvmField
        public val PULSE_NETWORK: AttributeKeyTemplate<String> = stringKeyTemplate(NETWORK)

        @JvmStatic
        public fun isNetworkType(type: String): Boolean = type.startsWith("$NETWORK.")
    }
}
