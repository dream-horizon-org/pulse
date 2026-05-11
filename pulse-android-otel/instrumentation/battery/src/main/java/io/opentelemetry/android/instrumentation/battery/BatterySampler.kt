/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.battery

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseDeviceAttributes
import com.pulse.utils.PulseOtelUtils
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal data class BatterySample(
    val levelPercent: Double,
    val plugged: PlugType,
)

internal enum class PlugType(
    internal val value: String,
) {
    BATTERY("battery"),
    USB("usb"),
    AC("ac"),
    WIRELESS("wireless"),
    DOCK("dock"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromInt(value: Int): PlugType =
            when (value) {
                0 -> BATTERY
                BatteryManager.BATTERY_PLUGGED_USB -> USB
                BatteryManager.BATTERY_PLUGGED_AC -> AC
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> WIRELESS
                BatteryManager.BATTERY_PLUGGED_DOCK -> DOCK
                else -> UNKNOWN
            }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun registerStickyBatteryIntentApi33(ctx: Context): Intent? =
    ctx.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        Context.RECEIVER_NOT_EXPORTED,
    )

@Suppress("DEPRECATION")
private fun registerStickyBatteryIntentLegacy(ctx: Context): Intent? =
    ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

@SuppressLint("UnspecifiedRegisterReceiverFlag")
private fun registerStickyBatteryIntent(ctx: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerStickyBatteryIntentApi33(ctx)
    } else {
        registerStickyBatteryIntentLegacy(ctx)
    }

internal fun readBatterySnapshot(application: Application): BatterySample? =
    PulseOtelUtils.runPulseCatching("BatterySampler:readBatterySnapshot") {
        val ctx = application.applicationContext
        val intent = registerStickyBatteryIntent(ctx) ?: return@runPulseCatching null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            null
        } else {
            val percent = level * 100L / scale.toDouble()
            val pluggedInt = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            BatterySample(levelPercent = percent, plugged = PlugType.fromInt(pluggedInt))
        }
    }

internal class BatterySampler(
    private val application: Application,
    private val logger: Logger,
    private val flushIntervalMs: Long,
    private val sampleIntervalMs: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val readSnapshot: (Application) -> BatterySample? = ::readBatterySnapshot,
) {
    private val active = AtomicBoolean(true)
    private val lock = Any()
    private val levelList = mutableListOf<Double>()
    private val pluggedList = mutableListOf<String>()
    private val timestampInMsList = mutableListOf<Long>()

    /** Last recorded sample (including after flush); used to drop consecutive duplicates. */
    private var lastRecordedSample: BatterySample? = null

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var samplingJob: Job? = null
    private var flushJob: Job? = null

    fun start() {
        samplingJob =
            scope.launch {
                while (isActive) {
                    collectSample()
                    delay(sampleIntervalMs)
                }
            }
        flushJob =
            scope.launch {
                while (isActive) {
                    delay(flushIntervalMs)
                    flushSamples()
                }
            }
    }

    fun shutdown() {
        samplingJob?.cancel()
        flushJob?.cancel()
    }

    fun setActive(isActive: Boolean) {
        active.set(isActive)
    }

    internal fun collectSample() {
        if (!active.get()) return
        readSnapshot(application)?.let { sample ->
            synchronized(lock) {
                if (sample == lastRecordedSample) return@synchronized
                lastRecordedSample = sample
                levelList.add(sample.levelPercent)
                pluggedList.add(sample.plugged.value)
                timestampInMsList.add(System.currentTimeMillis())
            }
        }
    }

    internal fun flushSamples() {
        val batch = drainSamples()
        if (batch.timestamps.isEmpty()) return
        val attributes =
            Attributes
                .builder()
                .put(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_LEVEL_ARRAY, batch.levels)
                .put(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_PLUGGED_ARRAY, batch.plugged)
                .put(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_TIMESTAMP_ARRAY, batch.timestamps)
                .put(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.BATTERY)
                .build()
        logger
            .logRecordBuilder()
            .setEventName(PulseAttributes.PulseTypeValues.BATTERY)
            .setAllAttributes(attributes)
            .emit()
    }

    private fun drainSamples(): SampleBatch =
        synchronized(lock) {
            val batch =
                SampleBatch(
                    levels = levelList.toList(),
                    plugged = pluggedList.toList(),
                    timestamps = timestampInMsList.toList(),
                )
            levelList.clear()
            pluggedList.clear()
            timestampInMsList.clear()
            batch
        }

    private data class SampleBatch(
        val levels: List<Double>,
        val plugged: List<String>,
        val timestamps: List<Long>,
    )

    companion object {
        val defaultSampleIntervalMs = if (PulseOtelUtils.isDebug()) 5_000L else 90_000L
    }
}
