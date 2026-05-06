/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * String keys align with Android `com.pulse.semconv.PulseDeviceAttributes` for OTLP parity.
 */

import Foundation

public enum PulseDeviceAttributes {
    /// Total device RAM in bytes (resource attribute).
    public static let systemMemorySize = "pulse.system.memory.size"

    /// Nominal full-charge battery capacity in microampere-hours (resource attribute), when available.
    public static let systemBatteryCapacityUah = "pulse.system.battery.capacity_uah"

    public static let systemMemoryUtilizationArray = "pulse.system.memory.utilization_array"
    public static let systemMemoryTimestampArray = "pulse.system.memory.timestamp_array"
    public static let appMemoryUtilizationArray = "pulse.app.memory.utilization_array"

    public static let systemBatteryLevelArray = "pulse.system.battery.level_array"
    public static let systemBatteryPluggedArray = "pulse.system.battery.plugged_array"
    public static let systemBatteryTimestampArray = "pulse.system.battery.timestamp_array"
}
