package io.opentelemetry.android.instrumentation.interaction.library

import com.pulse.android.core.InteractionErrorType
import com.pulse.android.core.InteractionFakeUtils
import com.pulse.android.core.InteractionLocalEventFakeUtils
import com.pulse.android.core.errorMessage
import com.pulse.android.remote.InteractionRemoteFakeUtils
import com.pulse.android.remote.models.InteractionConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InteractionInstrumentationTest {
    private val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val tracer =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
            .get("pulse.otel.interaction")

    private val attributeExtractors = listOf(InteractionDefaultAttributesExtractor())

    @BeforeEach
    fun resetExporter() {
        spanExporter.reset()
    }

    @Test
    fun `handleSuccessInteraction adds interaction events to span`() {
        val config =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                name = "add-to-cart",
                eventSequence =
                    listOf(
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("tap_sku"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("confirm"),
                    ),
            )
        val t0 = 10_000L
        val t1 = 50_000L
        val events =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("tap_sku", t0, mapOf("sku" to "42")),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("confirm", t1),
            )
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-events",
                config = config,
                events = events,
            )
        val status = InteractionFakeUtils.createFakeOngoingMatch(interaction, config)

        InteractionInstrumentation.handleSuccessInteraction(tracer, attributeExtractors, status)

        val span = spanExporter.finishedSpanItems.single()
        assertThat(span.name).isEqualTo("add-to-cart")
        assertThat(span.events).hasSize(2)
        assertThat(span.events[0].name).isEqualTo("tap_sku")
        assertThat(span.events[0].epochNanos).isEqualTo(t0)
        assertThat(span.events[0].attributes.get(AttributeKey.stringKey("sku"))).isEqualTo("42")
        assertThat(span.events[1].name).isEqualTo("confirm")
        assertThat(span.events[1].epochNanos).isEqualTo(t1)
    }

    @Test
    fun `handleSuccessInteraction adds local marker events to span after interaction events`() {
        val config =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                name = "checkout",
                eventSequence =
                    listOf(
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("start"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("done"),
                    ),
            )
        val tStart = 100L
        val tMarker = 150L
        val tDone = 200L
        val events =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("start", tStart),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("done", tDone),
            )
        val markers =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("marker_network", tMarker, mapOf("path" to "/pay")),
            )
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-markers",
                config = config,
                events = events,
                markers = markers,
            )
        val status = InteractionFakeUtils.createFakeOngoingMatch(interaction, config)

        InteractionInstrumentation.handleSuccessInteraction(tracer, attributeExtractors, status)

        val span = spanExporter.finishedSpanItems.single()
        assertThat(span.events).hasSize(3)
        assertThat(span.events[0].name).isEqualTo("start")
        assertThat(span.events[1].name).isEqualTo("done")
        assertThat(span.events[2].name).isEqualTo("marker_network")
        assertThat(span.events[2].epochNanos).isEqualTo(tMarker)
        assertThat(span.events[2].attributes.get(AttributeKey.stringKey("path"))).isEqualTo("/pay")
    }

    @Test
    fun `handleSuccessInteraction sets ERROR status for errored interaction`() {
        val config =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                name = "failing-flow",
                eventSequence =
                    listOf(
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("a"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("b"),
                    ),
            )
        val events =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("a", 1L),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("wrong", 2L),
            )
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-error",
                config = config,
                events = events,
                isSuccess = false,
                errorType = InteractionErrorType.SEQUENCE_VIOLATION,
                sequenceViolationExpectedEventName = "event2",
                sequenceViolationReceivedEventName = "wrong",
            )
        val status = InteractionFakeUtils.createFakeOngoingMatch(interaction, config)

        InteractionInstrumentation.handleSuccessInteraction(tracer, attributeExtractors, status)

        val span = spanExporter.finishedSpanItems.single()
        assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(span.status.description).isEqualTo(interaction.errorMessage)
        assertThat(span.status.description).contains("event2")
        assertThat(span.status.description).contains("wrong")
    }

    @Test
    fun `handleSuccessInteraction sets span start and end epoch nanos from interaction time span`() {
        val thresholdNanos = TimeUnit.MILLISECONDS.toNanos(5_000)
        val config =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                name = "timed-flow",
                eventSequence =
                    listOf(
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("open"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("close"),
                    ),
                thresholdInNanos = thresholdNanos,
            )
        val startNs = 1_234_567_890L
        val endNs = startNs + TimeUnit.MILLISECONDS.toNanos(300)
        val events =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("open", startNs),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("close", endNs),
            )
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-time",
                config = config,
                events = events,
            )
        val status = InteractionFakeUtils.createFakeOngoingMatch(interaction, config)

        InteractionInstrumentation.handleSuccessInteraction(tracer, attributeExtractors, status)

        val span = spanExporter.finishedSpanItems.single()
        assertThat(span.startEpochNanos).isEqualTo(startNs)
        assertThat(span.endEpochNanos).isEqualTo(endNs)
    }

    @Test
    fun `handleSuccessInteraction creates separate spans for different interactions`() {
        val seq =
            listOf(
                InteractionRemoteFakeUtils.createFakeInteractionEvent("e1"),
                InteractionRemoteFakeUtils.createFakeInteractionEvent("e2"),
            )
        val configAlpha =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                id = 10,
                name = "alpha-flow",
                eventSequence = seq,
            )
        val configBeta =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                id = 20,
                name = "beta-flow",
                eventSequence = seq,
            )
        val t = 1_000L

        fun buildFor(
            config: InteractionConfig,
            id: String,
        ) = InteractionFakeUtils.createFakeOngoingMatch(
            InteractionFakeUtils.createFakeInteraction(
                interactionId = id,
                config = config,
                events =
                    listOf(
                        InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("e1", t),
                        InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("e2", t + 100),
                    ),
            ),
            config,
        )

        InteractionInstrumentation.handleSuccessInteraction(
            tracer,
            attributeExtractors,
            buildFor(configAlpha, "span-a"),
        )
        InteractionInstrumentation.handleSuccessInteraction(
            tracer,
            attributeExtractors,
            buildFor(configBeta, "span-b"),
        )

        assertThat(spanExporter.finishedSpanItems).hasSize(2)
        assertThat(spanExporter.finishedSpanItems.map { it.name }).containsExactlyInAnyOrder(
            "alpha-flow",
            "beta-flow",
        )
    }
}
