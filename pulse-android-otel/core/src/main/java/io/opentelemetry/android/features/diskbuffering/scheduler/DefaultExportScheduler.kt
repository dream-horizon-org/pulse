/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.features.diskbuffering.scheduler

import com.pulse.utils.DiskFreeSpaceBytes
import com.pulse.utils.PulseLogger
import com.pulse.utils.RedactionUtils
import io.opentelemetry.android.features.diskbuffering.SignalFromDiskExporter
import io.opentelemetry.android.internal.services.periodicwork.PeriodicRunnable
import io.opentelemetry.android.internal.services.periodicwork.PeriodicWork
import java.io.IOException
import java.util.concurrent.TimeUnit

class DefaultExportScheduler(
    periodicWorkProvider: () -> PeriodicWork,
) : PeriodicRunnable(periodicWorkProvider) {
    @Volatile
    private var isShutDown: Boolean = false

    companion object {
        private val DELAY_BEFORE_NEXT_EXPORT_IN_MILLIS = TimeUnit.SECONDS.toMillis(10)
        private const val TAG = "DiskExport"
    }

    override fun onRun() {
        val exporter = SignalFromDiskExporter.get() ?: return

        try {
            do {
                val isExported = exporter.exportBatchOfEach()
            } while (isExported)
        } catch (e: IOException) {
            val free = DiskFreeSpaceBytes.forDataPartition()
            val diskPart = free?.let { " disk_available_bytes=$it" }.orEmpty()
            PulseLogger.logError(TAG, e) {
                "sdk.disk.read_failure signal=mixed error_class=${RedactionUtils.classifyError(e)} corrupted=false$diskPart"
            }
        }
    }

    fun shutdown() {
        isShutDown = true
    }

    override fun shouldStopRunning(): Boolean = isShutDown || SignalFromDiskExporter.get() == null

    override fun minimumDelayUntilNextRunInMillis(): Long = DELAY_BEFORE_NEXT_EXPORT_IN_MILLIS
}
