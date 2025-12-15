@file:Suppress("DEPRECATION")

package com.pulse.sampling.core

import com.pulse.otel.utils.toAttributes
import com.pulse.sampling.models.PulseProp
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.sampling.models.PulseSdkConfigFakeUtils.createFakeSignalMatchCondition
import com.pulse.sampling.models.PulseSignalFilterMode
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.opentelemetry.api.common.Value
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.data.Body
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@ExtendWith(MockKExtension::class)
class PulseSamplingSignalProcessorsTest {
    private companion object {
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher()

    private val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val logExporter: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private lateinit var spanProcessor: SpanProcessor
    private lateinit var logProcessor: LogRecordProcessor
    private lateinit var whitelistAllAllowedConfig: PulseSdkConfig
    private lateinit var whitelistAllAllowedProcessors: PulseSamplingSignalProcessors
    private lateinit var tracerProvider: SdkTracerProvider

    @BeforeEach
    fun setUp() {
        spanExporter.reset()
        logExporter.reset()
        spanProcessor = SimpleSpanProcessor.create(spanExporter)
        logProcessor = SimpleLogRecordProcessor.create(logExporter)
        tracerProvider =
            SdkTracerProvider
                .builder()
                .setResource(Resource.getDefault())
                .addSpanProcessor(spanProcessor)
                .build()
        whitelistAllAllowedConfig = PulseSdkConfigFakeUtils.createFakeConfig()
        whitelistAllAllowedProcessors = PulseSamplingSignalProcessors(whitelistAllAllowedConfig, signalMatcher)
    }

    @Nested
    inner class `With whitelist` {
        val whitelistWithRegexWithOneCharAndProp =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.WHITELIST,
                signalFilters = listOf(createFakeSignalMatchCondition("abc.", props = setOf(PulseProp("key1", "value1")))),
            )

        @Test
        fun `in span, filters the span only matching the regex and prop`() {
            val processors = PulseSamplingSignalProcessors(whitelistWithRegexWithOneCharAndProp, signalMatcher)
            val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

            sampledSpanProcessor.onEnd(createSpan("abc", emptyMap()))
            sampledSpanProcessor.onEnd(createSpan("abc", mapOf("key1" to "value1")))
            sampledSpanProcessor.onEnd(createSpan("abc1", emptyMap()))
            sampledSpanProcessor.onEnd(createSpan("abc1", mapOf("key1" to "value1")))
            sampledSpanProcessor.forceFlush()

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc1")
        }

        @Test
        fun `in log, filters the span only matching the regex and prop`() {
            val processors = PulseSamplingSignalProcessors(whitelistWithRegexWithOneCharAndProp, signalMatcher)
            val sampledLogProcessor = processors.SampledLogsProcessor(logProcessor)

            sampledLogProcessor.onEmit(Context.root(), createLogRecord("abc", emptyMap()))
            sampledLogProcessor.onEmit(Context.root(), createLogRecord("abc", mapOf("key1" to "value1")))
            sampledLogProcessor.onEmit(Context.root(), createLogRecord("abc1", emptyMap()))
            sampledLogProcessor.onEmit(Context.root(), createLogRecord("abc1", mapOf("key1" to "value1")))
            sampledLogProcessor.forceFlush()

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue }
                .isNotNull
                .extracting { it!!.asString() }
                .isEqualTo("abc1")
        }

        @Nested
        inner class `With all allowed` {
            @Test
            fun `in span, onEnd delegates processor when attributes is absent`() {
                val sampledSpanProcessor = whitelistAllAllowedProcessors.SampledSpanProcessor(spanProcessor)
                val realSpan = createSpan("test-span", emptyMap())

                sampledSpanProcessor.onEnd(realSpan)
                sampledSpanProcessor.forceFlush()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
                OpenTelemetryAssertions.assertThat(spanExporter.finishedSpanItems[0].attributes).isEmpty
            }

            @Test
            fun `in span, onEnd delegates processor when attributes is present`() {
                val sampledSpanProcessor = whitelistAllAllowedProcessors.SampledSpanProcessor(spanProcessor)
                val realSpan = createSpan("test-span", mapOf("key1" to "value1"))

                sampledSpanProcessor.onEnd(realSpan)
                sampledSpanProcessor.forceFlush()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry("key1", "value1")
            }

            @Test
            fun `in log, onEmit delegates processor when attributes is present`() {
                val sampledLogProcessor = whitelistAllAllowedProcessors.SampledLogsProcessor(logProcessor)
                val mockLogRecord = createLogRecord("test-log", mapOf("key1" to "value1"))
                sampledLogProcessor.onEmit(Context.root(), mockLogRecord)

                assertThat(logExporter.finishedLogRecordItems).hasSize(1)
                assertThat(logExporter.finishedLogRecordItems[0].body?.asString()).isEqualTo("test-log")
                OpenTelemetryAssertions
                    .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                    .containsEntry("key1", "value1")
            }

            @Test
            fun `in log, onEmit delegates processor when attributes is absent`() {
                val sampledLogProcessor = whitelistAllAllowedProcessors.SampledLogsProcessor(logProcessor)
                val mockLogRecord = createLogRecord("test-log", emptyMap())
                sampledLogProcessor.onEmit(Context.root(), mockLogRecord)

                assertThat(logExporter.finishedLogRecordItems).hasSize(1)
                assertThat(logExporter.finishedLogRecordItems[0].body?.asString()).isEqualTo("test-log")
                OpenTelemetryAssertions.assertThat(logExporter.finishedLogRecordItems[0].attributes).isEmpty
            }
        }
    }

    @Nested
    inner class `With blacklist` {
        val blackListWithRegexWithOneChar =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.BLACKLIST,
                signalFilters = listOf(createFakeSignalMatchCondition("abc.")),
            )

        val blackListWithRegexWithOneCharAndProp =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.BLACKLIST,
                signalFilters = listOf(createFakeSignalMatchCondition("abc.", props = setOf(PulseProp("key1", "value1")))),
            )

        val blackListWithRegexWithOneCharAndPropRegex =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.BLACKLIST,
                signalFilters = listOf(createFakeSignalMatchCondition("abc.", props = setOf(PulseProp("key1", "value1.")))),
            )

        @Test
        fun `in span, filters the span only matching the regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneChar, signalMatcher)
            val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

            sampledSpanProcessor.onEnd(createSpan("abc", emptyMap()))
            sampledSpanProcessor.onEnd(createSpan("abc1", emptyMap()))
            sampledSpanProcessor.forceFlush()

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc")
        }

        @Test
        fun `in span, filters the span only matching the regex and prop`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndProp, signalMatcher)
            val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

            sampledSpanProcessor.onEnd(createSpan("abc1", mapOf("key1" to "value1")))
            sampledSpanProcessor.forceFlush()

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in span, does not filters the span matching the name but not the prop`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndProp, signalMatcher)
            val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

            sampledSpanProcessor.onEnd(createSpan("abc1", mapOf("key1" to "value2")))
            sampledSpanProcessor.forceFlush()

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc1")
        }

        @Test
        fun `in span, filters the span only matching the regex and prop regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndPropRegex, signalMatcher)
            val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

            sampledSpanProcessor.onEnd(createSpan("abc1", mapOf("key1" to "value12")))
            sampledSpanProcessor.forceFlush()

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in span, does not filters the span matching the name but not the prop regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndPropRegex, signalMatcher)
            val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

            sampledSpanProcessor.onEnd(createSpan("abc1", mapOf("key1" to "value1")))
            sampledSpanProcessor.forceFlush()

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc1")
        }

        @Test
        fun `in log, filers the log only matching the regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneChar, signalMatcher)
            val sampledLogProcessor = processors.SampledLogsProcessor(logProcessor)
            sampledLogProcessor.onEmit(Context.root(), createLogRecord("abc", emptyMap()))
            sampledLogProcessor.onEmit(Context.root(), createLogRecord("abc1", emptyMap()))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue }
                .isNotNull
                .extracting { it!!.asString() }
                .isEqualTo("abc")
        }

        @Nested
        inner class `having all denied` {
            val blackListAllDenyConfig = PulseSdkConfigFakeUtils.createFakeConfig(filterMode = PulseSignalFilterMode.BLACKLIST)

            @Test
            fun `in span, filters the span without any props`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

                sampledSpanProcessor.onEnd(createSpan("test-span", emptyMap()))
                sampledSpanProcessor.forceFlush()

                assertThat(spanExporter.finishedSpanItems).isEmpty()
            }

            @Test
            fun `in span, filters the span with a prop`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledSpanProcessor = processors.SampledSpanProcessor(spanProcessor)

                sampledSpanProcessor.onEnd(createSpan("test-span", mapOf("key1" to "value1")))
                sampledSpanProcessor.forceFlush()

                assertThat(spanExporter.finishedSpanItems).isEmpty()
            }

            @Test
            fun `in log, filers the log without a prop`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledLogProcessor = processors.SampledLogsProcessor(logProcessor)
                val mockLogRecord = createLogRecord("test-log", emptyMap())
                sampledLogProcessor.onEmit(Context.root(), mockLogRecord)

                assertThat(logExporter.finishedLogRecordItems).isEmpty()
            }

            @Test
            fun `in log, filers the log with a prop`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledLogProcessor = processors.SampledLogsProcessor(logProcessor)
                val mockLogRecord = createLogRecord("test-log", mapOf("key1" to "value1"))
                sampledLogProcessor.onEmit(Context.root(), mockLogRecord)

                assertThat(logExporter.finishedLogRecordItems).isEmpty()
            }
        }
    }

    @Nested
    inner class `With attributes to drop` {
        private val attributesToDrop =
            listOf(
                createFakeSignalMatchCondition(
                    name = "test-span",
                    props = setOf(PulseProp("key1", "value1")),
                ),
            )
        private val attributesDroppingConfig = PulseSdkConfigFakeUtils.createFakeConfig(attributesToDrop = attributesToDrop)
        val attributesDroppingProcessors = PulseSamplingSignalProcessors(attributesDroppingConfig, signalMatcher)
        val attributesDroppingSpanExporter = attributesDroppingProcessors.FilteredSpanExporter(spanExporter)
        val attributesDroppingSpanProcessor: SpanProcessor = SimpleSpanProcessor.create(attributesDroppingSpanExporter)

        val attributesDroppingLogExporter = attributesDroppingProcessors.FilteredLogExporter(logExporter)
        val attributesDroppingLogProcessor: LogRecordProcessor = SimpleLogRecordProcessor.create(attributesDroppingLogExporter)

        @Test
        fun `in span, onEnd filters attributes when attributes match drop conditions`() {
            val sampledSpanProcessor = attributesDroppingProcessors.SampledSpanProcessor(attributesDroppingSpanProcessor)
            val mockSpan = createSpan("test-span", mapOf("key1" to "value1", "key2" to "value2"))

            sampledSpanProcessor.onEnd(mockSpan)

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .doesNotContainKey("key1")
        }

        @Test
        fun `in span, onEnd does not filter when no attributes match drop conditions`() {
            val mockSpan = createSpan("test-span", mapOf("otherKey" to "value1"))

            attributesDroppingSpanProcessor.onEnd(mockSpan)

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("otherKey", "value1")
        }

        @Test
        fun `in span, onEnd does not filter when no value doesn't match but key match`() {
            val mockSpan = createSpan("test-span", mapOf("key1" to "value2"))

            attributesDroppingSpanProcessor.onEnd(mockSpan)

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("key1", "value2")
        }

        @Test
        fun `in span, onEnd does not filter when no key doesn't match but value match`() {
            val mockSpan = createSpan("test-span", mapOf("key2" to "value1"))

            attributesDroppingSpanProcessor.onEnd(mockSpan)

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("key2", "value1")
        }

        @Test
        fun `in span, onEnd does not filter when name doesn't match but value and key match`() {
            val mockSpan = createSpan("test-span2", mapOf("key1" to "value1"))

            attributesDroppingSpanProcessor.onEnd(mockSpan)

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span2")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("key1", "value1")
        }

        @Test
        fun `in log, onEnd filters attributes when attributes match drop conditions`() {
            val sampleLogRecord = createLogRecord("test-span", mapOf("key1" to "value1", "key2" to "value2"))

            attributesDroppingLogProcessor.onEmit(Context.root(), sampleLogRecord)

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .doesNotContainKey("key1")
        }

        @Test
        fun `in log, onEnd does not filter when no attributes match drop conditions`() {
            val sampleLogRecord = createLogRecord("test-span", mapOf("otherKey" to "value1"))

            attributesDroppingLogProcessor.onEmit(Context.root(), sampleLogRecord)

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("otherKey", "value1")
        }

        @Test
        fun `in log, onEnd does not filter when no value doesn't match but key match`() {
            val sampleLogRecord = createLogRecord("test-span", mapOf("key1" to "value2"))

            attributesDroppingLogProcessor.onEmit(Context.root(), sampleLogRecord)

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("key1", "value2")
        }

        @Test
        fun `in log, onEnd does not filter when no key doesn't match but value match`() {
            val sampleLogRecord = createLogRecord("test-span", mapOf("key2" to "value1"))

            attributesDroppingLogProcessor.onEmit(Context.root(), sampleLogRecord)

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("key2", "value1")
        }

        @Test
        fun `in log, onEnd does not filter when name doesn't match but value and key match`() {
            val sampleLogRecord = createLogRecord("test-span2", mapOf("key1" to "value1"))

            attributesDroppingLogProcessor.onEmit(Context.root(), sampleLogRecord)

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span2")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("key1", "value1")
        }
    }

    @Test
    fun `in span, isEndRequired returns true`() {
        val sampledSpanProcessor = whitelistAllAllowedProcessors.SampledSpanProcessor(spanProcessor)
        assertThat(sampledSpanProcessor.isEndRequired()).isTrue
    }

    @Test
    fun `in span, shutdown delegates to delegateProcessor`() {
        val sampledSpanProcessor = whitelistAllAllowedProcessors.SampledSpanProcessor(spanProcessor)
        val result = sampledSpanProcessor.shutdown()
        assertThat(result.isSuccess).isTrue
    }

    @Test
    fun `in span, close delegates to delegateProcessor`() {
        val sampledSpanProcessor = whitelistAllAllowedProcessors.SampledSpanProcessor(spanProcessor)
        sampledSpanProcessor.close()
    }

    @Test
    fun `in span, forceFlush delegates to delegateProcessor`() {
        val sampledSpanProcessor = whitelistAllAllowedProcessors.SampledSpanProcessor(spanProcessor)
        val result = sampledSpanProcessor.forceFlush()
        assertThat(result.isSuccess).isTrue
    }

    private fun createSpan(
        name: String = "test-span",
        attributes: Map<String, Any?> = emptyMap(),
    ): ReadWriteSpan =
        otelTesting
            .openTelemetry
            .getTracer("testTracer")
            .spanBuilder(name)
            .startSpan()
            .setAllAttributes(attributes.toAttributes()) as ReadWriteSpan

    private fun createLogRecord(
        body: String,
        attributes: Map<String, Any?>,
        eventName: String? = null,
    ): ReadWriteLogRecord {
        val logRecordData =
            mockk<LogRecordData>()
                .apply {
                    every { this@apply.attributes } returns attributes.toAttributes()
                    every { this@apply.bodyValue } returns Value.of(body)
                    @Suppress("DEPRECATION")
                    every { this@apply.body } returns Body.string(body)
                    every { this@apply.eventName } returns eventName
                }
        return spyk<ReadWriteLogRecord>()
            .apply {
                every { this@apply.attributes } returns attributes.toAttributes()
                every { this@apply.bodyValue } returns Value.of(body)
                every { this@apply.eventName } returns eventName
                every { this@apply.toLogRecordData() } returns logRecordData
            }
    }
}
