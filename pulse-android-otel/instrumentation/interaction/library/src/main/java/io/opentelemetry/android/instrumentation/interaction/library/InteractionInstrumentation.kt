package io.opentelemetry.android.instrumentation.interaction.library

import com.google.auto.service.AutoService
import com.pulse.android.core.Interaction
import com.pulse.android.core.InteractionLocalEvent
import com.pulse.android.core.InteractionManager
import com.pulse.android.core.InteractionRunningStatus
import com.pulse.android.core.config.InteractionConfigFetcher
import com.pulse.android.core.config.InteractionConfigRestFetcher
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
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

fun interface InteractionAttributesExtractor : (AttributesBuilder, Interaction) -> Unit

@AutoService(AndroidInstrumentation::class)
class InteractionInstrumentation :
    AndroidInstrumentation,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private val additionalAttributeExtractors: MutableList<InteractionAttributesExtractor> =
        mutableListOf()
    private var interactionConfigFetcher: InteractionConfigFetcher? = null

    /**
     * Configure the interaction config fetcher.
     * In case not set defaults to "http://10.0.2.2:8080/v1/interaction-configs/" with [InteractionConfigRestFetcher]
     */
    fun setConfigFetcher(configFetcher: InteractionConfigFetcher): InteractionInstrumentation =
        apply {
            this.interactionConfigFetcher = configFetcher
        }

    val interactionManagerInstance by lazy {
        InteractionManager(
            interactionConfigFetcher ?: InteractionConfigRestFetcher(
                urlProvider = { "http://10.0.2.2:8080/v1/interaction-configs/" },
                headers = emptyMap(),
            ),
        )
    }

    private var tracer: Tracer? = null
    private var interactionListenerJob: Job? = null
    private val interactionHandledMap: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun install(ctx: InstallationContext) {
        additionalAttributeExtractors.add(InteractionDefaultAttributesExtractor())
        interactionListenerJob =
            launch {
                interactionManagerInstance.init()
                val localTracer =
                    tracer ?: ctx.openTelemetry.tracerProvider
                        .tracerBuilder("pulse.otel.interaction")
                        .build()
                tracer = localTracer
                interactionManagerInstance
                    .interactionTrackerStatesState
                    .flatMapMerge { it.asFlow() }
                    .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    .filter { it.interaction != null }
                    .collect { interactionRunningStatus ->
                        if (!interactionHandledMap.containsKey(interactionRunningStatus.interactionId)) {
                            handleSuccessInteraction(
                                localTracer,
                                additionalAttributeExtractors,
                                interactionRunningStatus,
                            )
                            interactionHandledMap[interactionRunningStatus.interactionId] = true
                        }
                    }
            }
    }

    override fun uninstall(ctx: InstallationContext) {
        interactionListenerJob?.cancel()
        interactionHandledMap.clear()
        super.uninstall(ctx)
    }

    /**
     * Adds a [InteractionAttributesExtractor] that can add Attributes from the [Interaction].
     */
    fun addAttributesExtractor(attributeExtractor: InteractionAttributesExtractor): InteractionInstrumentation {
        additionalAttributeExtractors.add(attributeExtractor)
        return this
    }

    override val name: String = INSTRUMENTATION_NAME

    companion object {
        @JvmStatic
        fun createSpanProcessor(interactionManager: InteractionManager): SpanProcessor =
            InteractionAttributesSpanAppender(interactionManager)

        @JvmStatic
        fun createLogProcessor(interactionManager: InteractionManager): LogRecordProcessor = InteractionLogListener(interactionManager)

        private fun handleSuccessInteraction(
            tracer: Tracer,
            additionalAttributeExtractors: List<InteractionAttributesExtractor>,
            interactionStatus: InteractionRunningStatus.OngoingMatch,
        ) {
            interactionStatus.interaction?.let { interaction ->
                // TODO: Investigate why timeSpanInNanos can be null (empty events list)
                // This safety check prevents crash but we need to understand root cause
                val timeSpanInNano = interaction.timeSpanInNanos ?: return@let
                val span =
                    tracer
                        .spanBuilder(interaction.name)
                        .apply {
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

        private infix fun List<InteractionLocalEvent>.addAsSpanEventsTo(span: Span) {
            this.forEach { localEvent ->
                span.addEvent(
                    localEvent.name,
                    localEvent.props.orEmpty().toAttributes(),
                    localEvent.timeInNano,
                    TimeUnit.NANOSECONDS,
                )
            }
        }

        const val INSTRUMENTATION_NAME = "android-interaction"
    }
}
