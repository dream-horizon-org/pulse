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
     * Project ID for multi-tenant applications.
     * Used in both HTTP headers (as "X-API-KEY") and as a global attribute (as "project.id").
     */
    @JvmField
    public val PROJECT_ID: AttributeKey<String> = stringKey("project.id")

    /**
     * Structured context for a click/tap. Model-readable format:
     * optional `label=X`, optional `element=image|button|chip` (semicolon-separated).
     * Set on app.widget.click only. Omitted when nothing extractable.
     */
    @JvmField
    public val APP_CLICK_CONTEXT: AttributeKey<String> = stringKey("app.click.context")

    public object AppClickContext {
        /** Indicates the clicked element is an image (ImageView/ImageButton, Compose Image/Icon). */
        public const val ELEMENT_IMAGE: String = "image"

        /** Indicates the clicked element is an icon (Compose Icon). Maps to image when Role.Image. */
        public const val ELEMENT_ICON: String = "icon"

        /** Indicates the clicked element is a button. */
        public const val ELEMENT_BUTTON: String = "button"

        /** Indicates the clicked element is a chip (Material Chip, FilterChip, etc.). */
        public const val ELEMENT_CHIP: String = "chip"

        /**
         * Builds `app.click.context` from optional label and element hint.
         * Examples: `label=Add to Cart; element=button`, `label=Email`, `element=image`.
         * Returns null when both are empty.
         */
        @JvmStatic
        public fun buildContext(
            label: String?,
            elementHint: String?,
        ): String? {
            val parts = mutableListOf<String>()
            label?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add("label=$it") }
            elementHint?.takeIf { it.isNotEmpty() }?.let { parts.add("element=$it") }
            return if (parts.isEmpty()) null else parts.joinToString("; ")
        }
    }

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
        public const val SESSION_REPLAY: String = "session_replay"
        private const val NETWORK: String = "network"
        public const val NETWORK_CHANGE: String = "network.change"

        @JvmField
        public val PULSE_NETWORK: AttributeKeyTemplate<String> = stringKeyTemplate(NETWORK)

        @JvmStatic
        public fun isNetworkType(type: String): Boolean = type.startsWith("$NETWORK.")
    }
}
