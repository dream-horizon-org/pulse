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
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.logs.data.Body
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PulseSamplingSignalProcessorsTest {
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher()

    private val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val logExporter: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private lateinit var whitelistAllAllowedConfig: PulseSdkConfig
    private lateinit var whitelistAllAllowedProcessors: PulseSamplingSignalProcessors

    @BeforeEach
    fun setUp() {
        spanExporter.reset()
        logExporter.reset()
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
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(
                listOf(
                    createSpanData("abc", emptyMap()),
                    createSpanData("abc", mapOf("key1" to "value1")),
                    createSpanData("abc1", emptyMap()),
                    createSpanData("abc1", mapOf("key1" to "value1")),
                ),
            )

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc1")
        }

        @Test
        fun `in log, filters the span only matching the regex and prop`() {
            val processors = PulseSamplingSignalProcessors(whitelistWithRegexWithOneCharAndProp, signalMatcher)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            sampledLogExporter.export(
                listOf(
                    createLogRecordData("abc", emptyMap()),
                    createLogRecordData("abc", mapOf("key1" to "value1")),
                    createLogRecordData("abc1", emptyMap()),
                    createLogRecordData("abc1", mapOf("key1" to "value1")),
                ),
            )

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
            fun `in span, export delegates exporter when attributes is absent`() {
                val sampledSpanExporter = whitelistAllAllowedProcessors.SampledSpanExporter(spanExporter)
                val realSpan = createSpanData("test-span", emptyMap())

                sampledSpanExporter.export(listOf(realSpan))

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
                OpenTelemetryAssertions.assertThat(spanExporter.finishedSpanItems[0].attributes).isEmpty
            }

            @Test
            fun `in span, export delegates exporter when attributes is present`() {
                val sampledSpanExporter = whitelistAllAllowedProcessors.SampledSpanExporter(spanExporter)
                val realSpan = createSpanData("test-span", mapOf("key1" to "value1"))

                sampledSpanExporter.export(listOf(realSpan))

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry("key1", "value1")
            }

            @Test
            fun `in log, export delegates exporter when attributes is present`() {
                val sampledLogExporter = whitelistAllAllowedProcessors.SampledLogExporter(logExporter)
                val mockLogRecord = createLogRecordData("test-log", mapOf("key1" to "value1"))
                sampledLogExporter.export(listOf(mockLogRecord))

                assertThat(logExporter.finishedLogRecordItems).hasSize(1)
                assertThat(logExporter.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
                OpenTelemetryAssertions
                    .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                    .containsEntry("key1", "value1")
            }

            @Test
            fun `in log, export delegates exporter when attributes is absent`() {
                val sampledLogExporter = whitelistAllAllowedProcessors.SampledLogExporter(logExporter)
                val mockLogRecord = createLogRecordData("test-log", emptyMap())
                sampledLogExporter.export(listOf(mockLogRecord))

                assertThat(logExporter.finishedLogRecordItems).hasSize(1)
                assertThat(logExporter.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
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
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(
                listOf(
                    createSpanData("abc", emptyMap()),
                    createSpanData("abc1", emptyMap()),
                ),
            )

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc")
        }

        @Test
        fun `in span, filters the span only matching the regex and prop`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndProp, signalMatcher)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(listOf(createSpanData("abc1", mapOf("key1" to "value1"))))

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in span, does not filters the span matching the name but not the prop`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndProp, signalMatcher)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(listOf(createSpanData("abc1", mapOf("key1" to "value2"))))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc1")
        }

        @Test
        fun `in span, filters the span only matching the regex and prop regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndPropRegex, signalMatcher)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(listOf(createSpanData("abc1", mapOf("key1" to "value12"))))

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in span, does not filters the span matching the name but not the prop regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneCharAndPropRegex, signalMatcher)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(listOf(createSpanData("abc1", mapOf("key1" to "value1"))))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("abc1")
        }

        @Test
        fun `in log, filers the log only matching the regex`() {
            val processors = PulseSamplingSignalProcessors(blackListWithRegexWithOneChar, signalMatcher)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)
            sampledLogExporter.export(
                listOf(
                    createLogRecordData("abc", emptyMap()),
                    createLogRecordData("abc1", emptyMap()),
                ),
            )

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
                val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

                sampledSpanExporter.export(listOf(createSpanData("test-span", emptyMap())))

                assertThat(spanExporter.finishedSpanItems).isEmpty()
            }

            @Test
            fun `in span, filters the span with a prop`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

                sampledSpanExporter.export(listOf(createSpanData("test-span", mapOf("key1" to "value1"))))

                assertThat(spanExporter.finishedSpanItems).isEmpty()
            }

            @Test
            fun `in log, filers the log without a prop`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledLogExporter = processors.SampledLogExporter(logExporter)
                val mockLogRecord = createLogRecordData("test-log", emptyMap())
                sampledLogExporter.export(listOf(mockLogRecord))

                assertThat(logExporter.finishedLogRecordItems).isEmpty()
            }

            @Test
            fun `in log, filers the log with a prop`() {
                val processors = PulseSamplingSignalProcessors(blackListAllDenyConfig, signalMatcher)
                val sampledLogExporter = processors.SampledLogExporter(logExporter)
                val mockLogRecord = createLogRecordData("test-log", mapOf("key1" to "value1"))
                sampledLogExporter.export(listOf(mockLogRecord))

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
        val attributesDroppingSpanExporter = attributesDroppingProcessors.SampledSpanExporter(spanExporter)

        val attributesDroppingLogExporter = attributesDroppingProcessors.SampledLogExporter(logExporter)

        @Test
        fun `in span, export filters attributes when attributes match drop conditions`() {
            val mockSpan = createSpanData("test-span", mapOf("key1" to "value1", "key2" to "value2"))

            attributesDroppingSpanExporter.export(listOf(mockSpan))

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
        fun `in span, export does not filter when no attributes match drop conditions`() {
            val mockSpan = createSpanData("test-span", mapOf("otherKey" to "value1"))

            attributesDroppingSpanExporter.export(listOf(mockSpan))

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
        fun `in span, export does not filter when no value doesn't match but key match`() {
            val mockSpan = createSpanData("test-span", mapOf("key1" to "value2"))

            attributesDroppingSpanExporter.export(listOf(mockSpan))

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
        fun `in span, export does not filter when no key doesn't match but value match`() {
            val mockSpan = createSpanData("test-span", mapOf("key2" to "value1"))

            attributesDroppingSpanExporter.export(listOf(mockSpan))

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
        fun `in span, export does not filter when name doesn't match but value and key match`() {
            val mockSpan = createSpanData("test-span2", mapOf("key1" to "value1"))

            attributesDroppingSpanExporter.export(listOf(mockSpan))

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
        fun `in log, export filters attributes when attributes match drop conditions`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("key1" to "value1", "key2" to "value2"))

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

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
        fun `in log, export does not filter when no attributes match drop conditions`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("otherKey" to "value1"))

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

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
        fun `in log, export does not filter when no value doesn't match but key match`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("key1" to "value2"))

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

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
        fun `in log, export does not filter when no key doesn't match but value match`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("key2" to "value1"))

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

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
        fun `in log, export does not filter when name doesn't match but value and key match`() {
            val sampleLogRecord = createLogRecordData("test-span2", mapOf("key1" to "value1"))

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

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
    fun `in span, shutdown delegates to delegateExporter`() {
        val sampledSpanExporter = whitelistAllAllowedProcessors.SampledSpanExporter(spanExporter)
        val result = sampledSpanExporter.shutdown()
        assertThat(result.isSuccess).isTrue
    }

    @Test
    fun `in span, close delegates to delegateExporter`() {
        val sampledSpanExporter = whitelistAllAllowedProcessors.SampledSpanExporter(spanExporter)
        sampledSpanExporter.close()
    }

    @Test
    fun `in span, flush delegates to delegateExporter`() {
        val sampledSpanExporter = whitelistAllAllowedProcessors.SampledSpanExporter(spanExporter)
        val result = sampledSpanExporter.flush()
        assertThat(result.isSuccess).isTrue
    }

    private fun createSpanData(
        name: String = "test-span",
        attributes: Map<String, Any?> = emptyMap(),
    ): SpanData =
        TestSpanData
            .builder()
            .setName(name)
            .setKind(SpanKind.INTERNAL)
            .setStatus(StatusData.unset())
            .setHasEnded(true)
            .setStartEpochNanos(0)
            .setEndEpochNanos(123)
            .setAttributes(attributes.toAttributes())
            .build()

    private fun createLogRecordData(
        body: String,
        attributes: Map<String, Any?>,
        eventName: String? = null,
    ): LogRecordData =
        mockk<LogRecordData>()
            .apply {
                every { this@apply.attributes } returns attributes.toAttributes()
                every { this@apply.bodyValue } returns Value.of(body)
                @Suppress("DEPRECATION")
                every { this@apply.body } returns Body.string(body)
                every { this@apply.eventName } returns eventName
            }
}
