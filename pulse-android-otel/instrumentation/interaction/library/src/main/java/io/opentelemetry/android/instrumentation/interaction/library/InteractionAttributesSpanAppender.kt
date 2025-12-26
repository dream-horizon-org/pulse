package io.opentelemetry.android.instrumentation.interaction.library

import com.pulse.android.core.InteractionManager
import com.pulse.android.core.InteractionRunningStatus
import com.pulse.android.core.runningIds
import com.pulse.android.core.runningNames
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseInteractionAttributes
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class InteractionAttributesSpanAppender(
    private val interactionManager: InteractionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SpanProcessor,
    CoroutineScope by CoroutineScope(SupervisorJob() + ioDispatcher) {
    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        createInteractionAttributes(interactionManager.interactionTrackerStatesState.value)?.let {
            span.setAllAttributes(it)
        }
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {
        val pulseType = span.attributes[PulseAttributes.PULSE_TYPE]
        if (pulseType != null && shouldAddToEvent(pulseType)) {
            interactionManager.addEvent(
                eventName = pulseType,
                params =
                    mapOf(
                        PulseAttributes.PULSE_SPAN_ID.key to span.spanContext.spanId,
                    ),
                eventTimeInNano = span.toSpanData().endEpochNanos,
            )
        }
    }

    override fun isEndRequired(): Boolean = true

    companion object {
        internal fun createInteractionAttributes(value: List<InteractionRunningStatus>): Attributes? {
            val ids = value.runningIds
            return if (ids.isNotEmpty()) {
                Attributes
                    .builder()
                    .apply {
                        put(
                            PulseInteractionAttributes.INTERACTION_NAMES,
                            value.runningNames,
                        )
                        put(
                            PulseInteractionAttributes.INTERACTION_IDS,
                            ids,
                        )
                    }.build()
            } else {
                null
            }
        }

        private fun shouldAddToEvent(pulseType: String) =
            pulseType in
                listOf(
                    PulseAttributes.PulseTypeValues.SCREEN_LOAD,
                    PulseAttributes.PulseTypeValues.APP_START,
                    PulseAttributes.PulseTypeValues.SCREEN_SESSION,
                ) || PulseAttributes.PulseTypeValues.isNetworkType(pulseType)
    }
}
