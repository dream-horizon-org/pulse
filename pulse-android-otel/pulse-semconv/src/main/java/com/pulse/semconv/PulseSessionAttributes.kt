package com.pulse.semconv

import com.pulse.semconv.PulseAttributes.PulseTypeValues
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

public object PulseSessionAttributes {
    @JvmField
    public val PULSE_SESSION_ANR_COUNT: AttributeKey<Long> = AttributeKey.longKey("pulse.session.anr.count")

    @JvmField
    public val PULSE_SESSION_CRASH_COUNT: AttributeKey<Long> = AttributeKey.longKey("pulse.session.crash.count")

    @JvmField
    public val PULSE_SESSION_CRASH_NON_FATAL: AttributeKey<Long> = AttributeKey.longKey("pulse.session.non_fatal.count")
    private val PULSE_SESSION_SLOW_FRAMES_COUNT =
        AttributeKey.longKey("pulse.session.jank.frozen.count")
    private val PULSE_SESSION_FROZEN_FRAMES_COUNT =
        AttributeKey.longKey("pulse.session.jank.slow.count")

    public fun createSessionEndAttributes(logEvents: Map<String, Long>): Attributes =
        Attributes
            .builder()
            .apply {
                logEvents[PulseTypeValues.ANR]?.let {
                    put(PULSE_SESSION_ANR_COUNT, it)
                }
                logEvents[PulseTypeValues.CRASH]?.let {
                    put(PULSE_SESSION_CRASH_COUNT, it)
                }
                logEvents[PulseTypeValues.SLOW]?.let {
                    put(PULSE_SESSION_SLOW_FRAMES_COUNT, it)
                }
                logEvents[PulseTypeValues.FROZEN]?.let {
                    put(PULSE_SESSION_FROZEN_FRAMES_COUNT, it)
                }
                logEvents[PulseTypeValues.NON_FATAL]?.let {
                    put(PULSE_SESSION_CRASH_NON_FATAL, it)
                }
            }.build()
}
