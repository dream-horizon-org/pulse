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
     * Structured context for a click/tap. Model-readable format: `label=X` when a human-readable
     * label was extracted. Set on app.widget.click only. Omitted when nothing extractable.
     */
    @JvmField
    public val APP_CLICK_CONTEXT: AttributeKey<String> = stringKey("app.click.context")

    /**
     * Quality of the click: "good" when the tap landed on an interactive target,
     * "dead" when it missed all clickable elements.
     */
    @JvmField
    public val CLICK_TYPE: AttributeKey<String> = stringKey("click.type")

    /** Number of taps in the rage-click cluster. Set when [CLICK_IS_RAGE] is true. */
    @JvmField
    public val CLICK_RAGE_COUNT: AttributeKey<Long> = AttributeKey.longKey("click.rage_count")

    /**
     * Normalised tap X coordinate: [APP_SCREEN_COORDINATE_X] / [VIEWPORT_WIDTH].
     * Range 0.0–1.0 (0 = left edge, 1 = right edge). Device-size-independent.
     */
    @JvmField
    public val APP_SCREEN_COORDINATE_NX: AttributeKey<Double> = AttributeKey.doubleKey("app.screen.coordinate.nx")

    /**
     * Normalised tap Y coordinate: [APP_SCREEN_COORDINATE_Y] / [VIEWPORT_HEIGHT].
     * Range 0.0–1.0 (0 = top edge, 1 = bottom edge). Device-size-independent.
     */
    @JvmField
    public val APP_SCREEN_COORDINATE_NY: AttributeKey<Double> = AttributeKey.doubleKey("app.screen.coordinate.ny")

    /**
     * True when the click is part of a rage cluster. Orthogonal to [CLICK_TYPE] —
     * a rage event is still classified as "good" (hit a target) or "dead" (missed),
     * with this flag and [CLICK_RAGE_COUNT] added alongside.
     */
    @JvmField
    public val CLICK_IS_RAGE: AttributeKey<Boolean> = AttributeKey.booleanKey("click.is_rage")

    public object ClickTypeValues {
        public const val GOOD: String = "good"
        public const val DEAD: String = "dead"
    }

    public object AppClickContext {
        /**
         * Builds `app.click.context` from an optional UI label.
         * Example: `label=Add to Cart`. Returns null when blank.
         */
        @JvmStatic
        public fun buildContext(label: String?): String? {
            val trimmed =
                label?.run {
                    trim().takeIf { it.isNotEmpty() }
                } ?: return null
            return "label=$trimmed"
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
        public const val APP_INTERACTIVE: String = "app_interactive"
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
        public const val MEMORY: String = "memory"
        public const val BATTERY: String = "battery"

        @JvmField
        public val PULSE_NETWORK: AttributeKeyTemplate<String> = stringKeyTemplate(NETWORK)

        @JvmStatic
        public fun isNetworkType(type: String): Boolean = type.startsWith("$NETWORK.")
    }
}
