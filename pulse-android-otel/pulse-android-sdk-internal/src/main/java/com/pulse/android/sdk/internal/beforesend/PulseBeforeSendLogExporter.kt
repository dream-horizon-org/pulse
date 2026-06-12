package com.pulse.android.sdk.internal.beforesend

import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.models.PulseLogRecordData
import com.pulse.android.api.otel.models.copy
import com.pulse.utils.PulseLogger
import com.pulse.utils.RedactionUtils
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

internal class PulseBeforeSendLogExporter(
    private val beforeSendData: PulseBeforeSendData,
    private val delegate: LogRecordExporter,
) : LogRecordExporter by delegate {
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        val pulseLogs =
            try {
                logs
                    .mapNotNull { log ->
                        val pulseLog =
                            log as? PulseLogRecordData ?: log.copy()
                        val afterGeneric = beforeSendData.beforeSend(pulseLog) ?: return@mapNotNull null
                        if (afterGeneric !is PulseLogRecordData) return@mapNotNull null
                        beforeSendData.beforeSendLog(afterGeneric)
                    }
            } catch (t: Throwable) {
                PulseLogger.logError(TAG, t) {
                    "sdk.beforesend.error signal=log_records error_class=${RedactionUtils.classifyError(t)}"
                }
                return CompletableResultCode.ofFailure()
            }
        return if (pulseLogs.isEmpty()) {
            CompletableResultCode.ofSuccess()
        } else {
            delegate.export(pulseLogs)
        }
    }

    override fun close() {
        delegate.close()
    }

    private companion object {
        private const val TAG = "BeforeSend"
    }
}
