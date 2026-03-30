/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.memory

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.pulse.semconv.PulseAttributes
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
    private val runtime = Runtime.getRuntime()
    private val active = AtomicBoolean(true)
    private val lock = Any()

    // System RAM (totalMem - availMem) and app heap (totalMemory - freeMemory) are sampled
    // at the same instant so they share a single timestamp list.
    private val systemUtilizationList = mutableListOf<Long>()
    private val appUtilizationList = mutableListOf<Long>()
    private val timestampInMsList = mutableListOf<Long>()

    private val memInfo = ActivityManager.MemoryInfo()

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
        activityManager.getMemoryInfo(memInfo)
        val appUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        synchronized(lock) {
            systemUtilizationList.add(memInfo.totalMem - memInfo.availMem)
            appUtilizationList.add(appUsedBytes)
            timestampInMsList.add(System.currentTimeMillis())
        }
    }

    internal fun flushSamples() {
        val batch = drainSamples()
        if (batch.timestamps.isEmpty()) return
        val attributes =
            Attributes
                .builder()
                .put(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY, batch.systemUtilization)
                .put(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_TIMESTAMP_ARRAY, batch.timestamps)
                .put(PulseDeviceAttributes.PULSE_APP_MEMORY_UTILIZATION_ARRAY, batch.appUtilization)
                .put(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.MEMORY)
                .build()
        logger
            .logRecordBuilder()
            .setEventName(PulseAttributes.PulseTypeValues.MEMORY)
            .setAllAttributes(attributes)
            .emit()
    }

    private fun drainSamples(): SampleBatch =
        synchronized(lock) {
            val batch =
                SampleBatch(
                    systemUtilization = systemUtilizationList.toList(),
                    appUtilization = appUtilizationList.toList(),
                    timestamps = timestampInMsList.toList(),
                )
            systemUtilizationList.clear()
            appUtilizationList.clear()
            timestampInMsList.clear()
            batch
        }

    private data class SampleBatch(
        val systemUtilization: List<Long>,
        val appUtilization: List<Long>,
        val timestamps: List<Long>,
    )

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS = 5_000L
    }
}
