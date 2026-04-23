/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.export

import com.pulse.utils.PulseLogger
import com.pulse.utils.RedactionUtils
import io.opentelemetry.api.internal.GuardedBy
import io.opentelemetry.sdk.common.CompletableResultCode
import java.nio.BufferOverflowException

/**
 * An exporter that delegates calls to a delegate exporter. Any data exported before the delegate
 * is set will be buffered in memory, up to the configured maximum number of entries.
 *
 * If the buffer is full, the exporter will drop any new signals.
 *
 * Export, flush, and shutdown lambdas are invoked on the delegate once it is set; the max buffer
 * size caps buffered items before drops; the log type string labels diagnostic logs.
 */
internal class DelegatingExporter<D, T>(
    private val doExport: D.(data: Collection<T>) -> CompletableResultCode,
    private val doFlush: D.() -> CompletableResultCode,
    private val doShutdown: D.() -> CompletableResultCode,
    private val maxBufferedData: Int,
    private val logType: String,
) {
    private val logTypeToken = logType.replace(' ', '_')

    private companion object {
        private const val TAG = "SdkQueue"
        private const val EXPORT_DIAG_TAG = "ExportDiag"
    }
    private val lock = Any()

    @GuardedBy("lock")
    private var delegate: D? = null

    @GuardedBy("lock")
    private val buffer = arrayListOf<T>()

    @GuardedBy("lock")
    private var pendingExport: CompletableResultCode? = null

    @GuardedBy("lock")
    private var pendingFlush: CompletableResultCode? = null

    @GuardedBy("lock")
    private var pendingShutdown: CompletableResultCode? = null

    /**
     * Sets the delegate for this exporter.
     *
     * Any buffered data will be written to the delegate followed by a flush and shut down if
     * [flush] and/or [shutdown] has been called prior to this call.
     *
     * @param delegate the delegate to set
     * @throws IllegalStateException if a delegate has already been set
     */
    fun setDelegate(delegate: D) {
        synchronized(lock) {
            check(this.delegate == null) { "A delegate has already been set." }
            this.delegate = delegate
        }
        // Exporting outside of the synchronized block could lead to an out of order export
        // but export order shouldn't matter so this is fine. It's better to avoid calling external
        // code from within the synchronized block.
        pendingExport?.setTo(exportThroughDelegate(delegate, buffer))
        pendingFlush?.setTo(delegate.doFlush())
        pendingShutdown?.setTo(delegate.doShutdown())
        synchronized(lock) {
            pendingExport = null
            pendingFlush = null
            pendingShutdown = null
        }
        clearBuffer()
    }

    /**
     * Exports the given data using the [doExport] lambda. If the delegate is not yet set an export
     * will be scheduled and executed when the delegate is set.
     *
     * @param data the data to export.
     * @return the result. If the delegate is set then the result from it will be returned,
     *   otherwise a result is returned which will complete when the delegate is set and the data
     *   has been exported. If all of the data was dropped then a failure is returned.
     */
    fun export(data: Collection<T>): CompletableResultCode =
        withDelegate(
            ifSet = { exportThroughDelegate(this, data) },
            ifNotSet = {
                val amountToTake = maxBufferedData - buffer.size
                buffer.addAll(data.take(amountToTake))
                if (amountToTake < data.size) {
                    val dropped = data.size - amountToTake
                    PulseLogger.logWarn(TAG) {
                        "sdk.queue signal=$logTypeToken depth=${buffer.size} overflow=true dropped_count=$dropped"
                    }
                }

                // If all the data was dropped we return an exception
                if (amountToTake == 0 && data.isNotEmpty()) {
                    PulseLogger.logError(TAG, BufferOverflowException()) {
                        "sdk.export.drop signal=$logTypeToken dropped_count=${data.size} reason=queue_full"
                    }
                    CompletableResultCode.ofExceptionalFailure(BufferOverflowException())
                } else {
                    pendingExport
                        ?: CompletableResultCode().also { pendingExport = it }
                }
            },
        )

    /**
     * Flushes the exporter using the [doFlush] lambda. If the delegate is not yet set a flush will
     * be scheduled and executed when the delegate is set.
     *
     * @return the result. If the delegate is set then the result from it will be returned,
     *   otherwise a result is returned which will complete when the delegate is set and has been
     *   flushed.
     */
    fun flush(): CompletableResultCode =
        withDelegate(
            ifSet = doFlush,
            ifNotSet = { pendingFlush ?: CompletableResultCode().also { pendingFlush = it } },
        )

    /**
     * Shuts down the exporter using the [doShutdown]. If the delegate is not yet set a shut down
     * will be scheduled and executed when the delegate is set.
     *
     * @return the result. If the delegate is set then the result from it will be returned,
     *   otherwise a result is returned which will complete when the delegate is set and has been
     *   shut down.
     */
    fun shutdown(): CompletableResultCode =
        withDelegate(
            ifSet = doShutdown,
            ifNotSet = { pendingShutdown ?: CompletableResultCode().also { pendingShutdown = it } },
        )

    private fun clearBuffer() {
        buffer.clear()
        buffer.trimToSize()
    }

    private fun exportThroughDelegate(
        delegate: D,
        data: Collection<T>,
    ): CompletableResultCode {
        val startNs = System.nanoTime()
        val batchSize = data.size
        val result = doExport(delegate, data)
        result.whenComplete {
            logSdkExport(batchSize, System.nanoTime() - startNs, result)
        }
        return result
    }

    private fun logSdkExport(
        batchSize: Int,
        durationNs: Long,
        result: CompletableResultCode,
    ) {
        val ms = durationNs / 1_000_000
        if (result.isSuccess) {
            PulseLogger.logDebug(EXPORT_DIAG_TAG) {
                "sdk.export signal=$logTypeToken success=true latency_ms=$ms batch_size=$batchSize"
            }
        } else {
            val err =
                result.failureThrowable?.let { RedactionUtils.classifyError(it) } ?: "unknown"
            PulseLogger.logWarn(EXPORT_DIAG_TAG) {
                "sdk.export signal=$logTypeToken success=false latency_ms=$ms batch_size=$batchSize error_class=$err"
            }
        }
    }

    private inline fun <R> withDelegate(
        ifSet: D.() -> R,
        ifNotSet: () -> R,
    ): R {
        val delegate =
            synchronized(lock) {
                delegate ?: return ifNotSet()
            }
        // We interact with the delegate outside of the synchronized block to avoid any potential
        // deadlocks due to reentrant calls
        return delegate.ifSet()
    }

    private fun CompletableResultCode.setTo(other: CompletableResultCode) {
        other.whenComplete {
            if (other.isSuccess) {
                succeed()
            } else {
                val throwable = other.failureThrowable
                if (throwable == null) {
                    fail()
                } else {
                    failExceptionally(throwable)
                }
            }
        }
    }
}
