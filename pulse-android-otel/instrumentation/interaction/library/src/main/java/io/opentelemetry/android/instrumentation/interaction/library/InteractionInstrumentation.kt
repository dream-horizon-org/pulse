package io.opentelemetry.android.instrumentation.interaction.library

import com.google.auto.service.AutoService
import com.pulse.android.core.Interaction
import com.pulse.android.core.InteractionLocalEvent
import com.pulse.android.core.InteractionManager
import com.pulse.android.core.InteractionRunningStatus
import com.pulse.android.core.events
import com.pulse.android.core.isErrored
import com.pulse.android.core.markerEvents
import com.pulse.android.core.timeSpanInNanos
import com.pulse.otel.utils.toAttributes
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TracerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

fun interface InteractionAttributesExtractor : (AttributesBuilder, Interaction) -> Unit

@AutoService(AndroidInstrumentation::class)
class InteractionInstrumentation :
    AndroidInstrumentation,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private val additionalAttributeExtractors: MutableList<InteractionAttributesExtractor> =
        mutableListOf()

    override fun install(ctx: InstallationContext) {
        additionalAttributeExtractors.add(DefaultInteractionAttributesExtractor())
        launch {
            InteractionManager.instance.init()
            InteractionManager.instance.interactionTrackerStatesState.collect { interactionRunningStatuses ->
                handleSuccessInteraction(
                    ctx.openTelemetry.tracerProvider,
                    additionalAttributeExtractors,
                    interactionRunningStatuses
                )
            }
        }
    }

    /**
     * Adds a [InteractionAttributesExtractor] that can add Attributes from the [Interaction].
     */
    fun addAttributesExtractor(attributeExtractor: InteractionAttributesExtractor): InteractionInstrumentation {
        additionalAttributeExtractors.add(attributeExtractor)
        return this
    }

    override val name: String = "android-interaction"

    companion object {
        fun handleSuccessInteraction(
            tracerProvider: TracerProvider,
            additionalAttributeExtractors: List<InteractionAttributesExtractor>,
            interactionStatuses: List<InteractionRunningStatus>
        ) {
            val tracer = tracerProvider
                .tracerBuilder("pulse.otel.interaction")
                .build()
            interactionStatuses.map { interactionRunningStatus ->
                when (interactionRunningStatus) {
                    is InteractionRunningStatus.NoOngoingMatch -> {
                        /* no-op */
                    }

                    is InteractionRunningStatus.OngoingMatch -> {
                        interactionRunningStatus.interaction?.let { interaction ->
                            val timeSpanInNano = interaction.timeSpanInNanos
                            val span = tracer.spanBuilder(interaction.name).apply {
                                setNoParent()
                                val attributesBuilder = Attributes.builder()
                                additionalAttributeExtractors.forEach(
                                    Consumer { extractor: InteractionAttributesExtractor ->
                                        extractor(attributesBuilder, interaction)
                                    },
                                )
                                setAllAttributes(attributesBuilder.build())
                                setStartTimestamp(timeSpanInNano.first, TimeUnit.NANOSECONDS)
                            }.startSpan()
                            interaction.events addAsSpanEventsTo span
                            interaction.markerEvents addAsSpanEventsTo span
                            if (interaction.isErrored) {
                                span.setStatus(StatusCode.ERROR)
                            }
                            span.end(timeSpanInNano.second, TimeUnit.NANOSECONDS)
                        }
                    }
                }
            }
        }

        private infix fun List<InteractionLocalEvent>.addAsSpanEventsTo(span: Span) {
            this.forEach { localEvent ->
                span.addEvent(
                    localEvent.name,
                    localEvent.props.orEmpty().toAttributes(),
                    localEvent.timeInNano,
                    TimeUnit.NANOSECONDS
                )
            }
        }

        internal const val LOG_TAG = "InteractionInstr"
    }
}
