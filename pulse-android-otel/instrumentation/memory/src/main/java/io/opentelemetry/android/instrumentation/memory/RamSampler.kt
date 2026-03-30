/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.memory

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.pulse.semconv.PulseDeviceAttributes
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

internal class RamSampler(
    application: Application,
    private val logger: Logger,
    private val flushIntervalMs: Long,
    private val sampleIntervalMs: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val activityManager =
        application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager?
    private val active = AtomicBoolean(true)
    private val lock = Any()

    private val utilizationInBytesList = mutableListOf<Long>()
    private val timestampInMsList = mutableListOf<Long>()

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
        val activityManager = activityManager ?: return
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        synchronized(lock) {
            utilizationInBytesList.add(memInfo.totalMem - memInfo.availMem)
            timestampInMsList.add(System.currentTimeMillis())
        }
    }

    internal fun flushSamples() {
        val (utilization, timestamps) = drainSamples()
        if (utilization.isEmpty()) return
        val attributes =
            Attributes
                .builder()
                .put(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY, utilization)
                .put(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_TIMESTAMP_ARRAY, timestamps)
                .build()
        logger
            .logRecordBuilder()
            .setAllAttributes(attributes)
            .emit()
    }

    private fun drainSamples(): Pair<List<Long>, List<Long>> =
        synchronized(lock) {
            val utilization = utilizationInBytesList.toList()
            val timestamps = timestampInMsList.toList()
            utilizationInBytesList.clear()
            timestampInMsList.clear()
            utilization to timestamps
        }

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS = 5_000L
    }
}
