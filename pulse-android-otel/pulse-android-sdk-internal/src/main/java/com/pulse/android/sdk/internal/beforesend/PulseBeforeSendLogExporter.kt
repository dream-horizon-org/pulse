package com.pulse.android.sdk.internal.beforesend

import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.models.PulseLogRecordData
import com.pulse.android.api.otel.models.copy
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

internal class PulseBeforeSendLogExporter(
    private val beforeSendData: PulseBeforeSendData,
    private val delegate: LogRecordExporter,
) : LogRecordExporter by delegate {
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        val pulseLogs =
            logs
                .mapNotNull { log ->
                    val pulseLog =
                        log as? PulseLogRecordData ?: log.copy()
                    val afterGeneric = beforeSendData.beforeSend(pulseLog) ?: return@mapNotNull null
                    if (afterGeneric !is PulseLogRecordData) return@mapNotNull null
                    beforeSendData.beforeSendLog(afterGeneric)
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
}
