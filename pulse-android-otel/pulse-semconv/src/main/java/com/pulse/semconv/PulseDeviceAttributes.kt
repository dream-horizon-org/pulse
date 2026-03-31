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
     * Total device RAM in bytes.
     */
    @JvmField
    public val PULSE_SYSTEM_MEMORY_SIZE: AttributeKey<Long> =
        AttributeKey.longKey("pulse.system.memory.size")

    /**
     * Estimated nominal full-charge battery capacity in **microampere-hours (µAh)**.
     * Omitted when unavailable.
     */
    @JvmField
    public val PULSE_SYSTEM_BATTERY_CAPACITY_UAH: AttributeKey<Long> =
        AttributeKey.longKey("pulse.system.battery.capacity_uah")

    /**
     * Sampled device RAM utilization values, each expressed as used bytes (`totalMem - availMem`).
     * Emitted as a log attribute alongside [PULSE_SYSTEM_MEMORY_TIMESTAMP_ARRAY].
     */
    @JvmField
    public val PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY: AttributeKey<List<Long>> =
        AttributeKey.longArrayKey("pulse.system.memory.utilization_array")

    /**
     * Epoch-millisecond timestamps at which each entry in
     * [PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY] was recorded.
     */
    @JvmField
    public val PULSE_SYSTEM_MEMORY_TIMESTAMP_ARRAY: AttributeKey<List<Long>> =
        AttributeKey.longArrayKey("pulse.system.memory.timestamp_array")

    /**
     * Sampled app heap utilization values, each expressed as used bytes
     * Emitted as a log attribute alongside [PULSE_SYSTEM_MEMORY_TIMESTAMP_ARRAY].
     */
    @JvmField
    public val PULSE_APP_MEMORY_UTILIZATION_ARRAY: AttributeKey<List<Long>> =
        AttributeKey.longArrayKey("pulse.app.memory.utilization_array")

    /**
     * Remaining battery charge for each sample, as a percentage **0–100**.
     * Same repeated samples are dropped
     * Emitted with [PULSE_SYSTEM_BATTERY_PLUGGED_ARRAY] and [PULSE_SYSTEM_BATTERY_TIMESTAMP_ARRAY].
     */
    @JvmField
    public val PULSE_SYSTEM_BATTERY_LEVEL_ARRAY: AttributeKey<List<Double>> =
        AttributeKey.doubleArrayKey("pulse.system.battery.level_array")

    /**
     * Power source for each sample: `battery` when unplugged, or one of
     * `usb`, `ac`, `wireless`, `dock` when charging; `unknown` if the plug state cannot be mapped.
     */
    @JvmField
    public val PULSE_SYSTEM_BATTERY_PLUGGED_ARRAY: AttributeKey<List<String>> =
        AttributeKey.stringArrayKey("pulse.system.battery.plugged_array")

    /**
     * Epoch-millisecond timestamps for each battery sample.
     */
    @JvmField
    public val PULSE_SYSTEM_BATTERY_TIMESTAMP_ARRAY: AttributeKey<List<Long>> =
        AttributeKey.longArrayKey("pulse.system.battery.timestamp_array")
}
