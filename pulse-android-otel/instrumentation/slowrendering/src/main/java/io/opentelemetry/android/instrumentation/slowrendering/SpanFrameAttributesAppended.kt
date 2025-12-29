package io.opentelemetry.android.instrumentation.slowrendering

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.internal.ExtendedSpanProcessor

class SpanFrameAttributesAppended : ExtendedSpanProcessor {
    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        // no-op
    }

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired(): Boolean = false

    override fun onEnding(span: ReadWriteSpan) {
        val spanData = span.toSpanData()
        span.setAllAttributes(
            getFrameAttributes(
                FrameDataHelper.createCumulativeFrameMetric(
                    spanData.startEpochNanos / 1000,
                    spanData.endEpochNanos / 1000,
                ),
            ),
        )
    }

    override fun isOnEndingRequired(): Boolean = true

    internal companion object {
        private fun getFrameAttributes(first: FrameDataHelper.CumulativeFrameData): Attributes =
            Attributes
                .builder()
                .apply {
                    put(
                        "app.interaction.analysed_frame_count",
                        first.analysedFrameCount,
                    )
                    put(
                        "app.interaction.unanalysed_frame_count",
                        first.unanalysedFrameCount,
                    )
                    put(
                        "app.interaction.slow_frame_count",
                        first.slowFrameCount,
                    )
                    put(
                        "app.interaction.frozen_frame_count",
                        first.frozenFrameCount,
                    )
                }.build()
    }
}
