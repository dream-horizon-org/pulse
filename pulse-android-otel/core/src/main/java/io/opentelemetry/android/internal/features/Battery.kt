/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.internal.features

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.pulse.utils.PulseLogger
import com.pulse.utils.PulseOtelUtils

/**
 * Full-charge capacity ≈ remaining charge (µAh) × 100 / capacity percent, when both
 * [BatteryManager] properties are supported and percent is in 1…100.
 */
internal fun readBatteryFullCapacityMicroAh(application: Application): Long? =
    PulseOtelUtils.runPulseCatching("readBatteryFullCapacityMicroAh") {
        val batteryManager =
            application.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return null
        val percent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 1..100) return null
        val remainingUah = readChargeCounterMicroAh(batteryManager) ?: return null
        if (remainingUah < 0) return null
        (remainingUah * 100L / percent).also {
            PulseLogger.logDebug("readBatteryFullCap") {
                "batterCap = $it"
            }
        }
    }

private fun readChargeCounterMicroAh(batteryManager: BatteryManager): Long? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        readChargeCounterMicroAhApi31(batteryManager)
    } else {
        readChargeCounterMicroAhLegacy(batteryManager)
    }

@RequiresApi(Build.VERSION_CODES.S)
private fun readChargeCounterMicroAhApi31(batteryManager: BatteryManager): Long? {
    val v = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    return if (v == Long.MIN_VALUE || v < 0) null else v
}

private fun readChargeCounterMicroAhLegacy(batteryManager: BatteryManager): Long? {
    val v = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    return if (v == Integer.MIN_VALUE || v < 0) null else v.toLong()
}
