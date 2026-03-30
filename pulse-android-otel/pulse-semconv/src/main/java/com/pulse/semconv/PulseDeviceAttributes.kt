package com.pulse.semconv

import io.opentelemetry.api.common.AttributeKey

public object PulseDeviceAttributes {
    @JvmField
    public val DEVICE_SCREEN_WIDTH: AttributeKey<Long> = AttributeKey.longKey("device.screen.width")

    @JvmField
    public val DEVICE_SCREEN_HEIGHT: AttributeKey<Long> = AttributeKey.longKey("device.screen.height")

    /**
     * Simplified aspect ratio of the screen as "width:height", e.g. "9:20".
     * Computed from [DEVICE_SCREEN_WIDTH] and [DEVICE_SCREEN_HEIGHT] via GCD reduction.
     * Consistent across Android (dp) and iOS (pt) for standard display densities.
     */
    @JvmField
    public val DEVICE_SCREEN_ASPECT_RATIO: AttributeKey<String> = AttributeKey.stringKey("device.screen.aspect_ratio")

    /**
     * Sampled device RAM utilization values, each expressed as used bytes (`totalMem - availMem`).
     * Emitted as a log attribute alongside [PULSE_SYSTEM_MEMORY_UTILIZATION_TIMESTAMP_ARRAY].
     */
    @JvmField
    public val PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY: AttributeKey<List<Long>> =
        AttributeKey.longArrayKey("pulse.system.memory.utilization_array")

    /**
     * Epoch-millisecond timestamps at which each entry in
     * [PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY] was recorded.
     */
    @JvmField
    public val PULSE_SYSTEM_MEMORY_UTILIZATION_TIMESTAMP_ARRAY: AttributeKey<List<Long>> =
        AttributeKey.longArrayKey("pulse.system.memory.utilization_timestamp_array")
}
