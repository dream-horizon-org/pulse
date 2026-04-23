package com.pulse.android.sdk.internal.beforesend

import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.models.PulseSpanData
import com.pulse.android.api.otel.models.copy
import com.pulse.utils.PulseLogger
import com.pulse.utils.RedactionUtils
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class PulseBeforeSendSpanExporter(
    private val beforeSendData: PulseBeforeSendData,
    private val delegate: SpanExporter,
) : SpanExporter by delegate {
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val pulseSpans =
            try {
                spans
                    .mapNotNull { span ->
                        val pulseSpan =
                            span as? PulseSpanData ?: span.copy()
                        val afterGeneric = beforeSendData.beforeSend(pulseSpan) ?: return@mapNotNull null
                        if (afterGeneric !is PulseSpanData) return@mapNotNull null
                        beforeSendData.beforeSendSpan(afterGeneric)
                    }
            } catch (t: Throwable) {
                PulseLogger.logError(TAG, t) {
                    "sdk.beforesend.error signal=span error_class=${RedactionUtils.classifyError(t)}"
                }
                return CompletableResultCode.ofFailure()
            }
        return if (pulseSpans.isEmpty()) {
            CompletableResultCode.ofSuccess()
        } else {
            delegate.export(pulseSpans)
        }
    }

    override fun close() {
        delegate.close()
    }

    private companion object {
        private const val TAG = "BeforeSend"
    }
}
