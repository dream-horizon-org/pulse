package io.opentelemetry.android.instrumentation.slowrendering

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.internal.ExtendedSpanProcessor
import java.util.concurrent.ConcurrentHashMap

class SpanFrameAttributesAppended : ExtendedSpanProcessor {
    private val spanMap =
        ConcurrentHashMap<Span, SlowRenderListener.Companion.CumulativeFrameData>()

    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        spanMap[span] = SlowRenderListener.createCumulativeFrameMetric()
    }

    override fun isStartRequired() = true

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired(): Boolean = false

    override fun onEnding(span: ReadWriteSpan) {
        spanMap[span]?.let {
            span.setAllAttributes(
                getFrameAttributes(
                    it,
                    SlowRenderListener.createCumulativeFrameMetric(),
                ),
            )
        }
    }

    override fun isOnEndingRequired(): Boolean = true

    internal companion object {
        private fun getFrameAttributes(
            first: SlowRenderListener.Companion.CumulativeFrameData,
            second: SlowRenderListener.Companion.CumulativeFrameData,
        ): Attributes =
            Attributes
                .builder()
                .apply {
                    put(
                        "app.interaction.analysed_frame_count",
                        second.analysedFrameCount - first.analysedFrameCount,
                    )
                    put(
                        "app.interaction.unanalysed_frame_count",
                        second.unanalysedFrameCount - first.unanalysedFrameCount,
                    )
                    put(
                        "app.interaction.slow_frame_count",
                        second.slowFrameCount - first.slowFrameCount,
                    )
                    put(
                        "app.interaction.frozen_frame_count",
                        second.frozenFrameCount - first.frozenFrameCount,
                    )
                }.build()
    }
}
