@file:Suppress("DEPRECATION", "ClassName")

package com.pulse.sampling.core

import android.content.Context
import com.pulse.otel.utils.toAttributes
import com.pulse.sampling.models.PulseAttributeType
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.sampling.models.PulseSdkConfigFakeUtils.createFakeSignalMatchCondition
import com.pulse.sampling.models.PulseSdkName
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
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.security.SecureRandom
import java.util.Random

@ExtendWith(MockKExtension::class)
class PulseSamplingSignalProcessorsTest {
    private val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val logExporter: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private val metricExporter: InMemoryMetricExporter = InMemoryMetricExporter.create()
    private lateinit var whitelistAllAllowedConfig: PulseSdkConfig
    private lateinit var whitelistAllAllowedProcessors: PulseSamplingSignalProcessors

    @BeforeEach
    fun setUp() {
        spanExporter.reset()
        logExporter.reset()
        metricExporter.reset()
        whitelistAllAllowedConfig = PulseSdkConfigFakeUtils.createFakeConfig()
        whitelistAllAllowedProcessors = createSamplingSignalProcessors(whitelistAllAllowedConfig)
    }

    @Nested
    inner class `With whitelist` {
        val whitelistWithRegexWithOneCharAndProp =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.WHITELIST,
                signalFilters =
                    listOf(
                        createFakeSignalMatchCondition("abc.", props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1"))),
                    ),
            )

        @Test
        fun `in span, filters the span only matching the regex and prop`() {
            val processors = createSamplingSignalProcessors(whitelistWithRegexWithOneCharAndProp)
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
            val processors = createSamplingSignalProcessors(whitelistWithRegexWithOneCharAndProp)
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
                signalFilters =
                    listOf(
                        createFakeSignalMatchCondition("abc.", props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1"))),
                    ),
            )

        val blackListWithRegexWithOneCharAndPropRegex =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.BLACKLIST,
                signalFilters =
                    listOf(
                        createFakeSignalMatchCondition("abc.", props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1."))),
                    ),
            )

        @Test
        fun `in span, filters the span only matching the regex`() {
            val processors = createSamplingSignalProcessors(blackListWithRegexWithOneChar)
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
            val processors = createSamplingSignalProcessors(blackListWithRegexWithOneCharAndProp)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(listOf(createSpanData("abc1", mapOf("key1" to "value1"))))

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in span, does not filters the span matching the name but not the prop`() {
            val processors = createSamplingSignalProcessors(blackListWithRegexWithOneCharAndProp)
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
            val processors = createSamplingSignalProcessors(blackListWithRegexWithOneCharAndPropRegex)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            sampledSpanExporter.export(listOf(createSpanData("abc1", mapOf("key1" to "value12"))))

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in span, does not filters the span matching the name but not the prop regex`() {
            val processors = createSamplingSignalProcessors(blackListWithRegexWithOneCharAndPropRegex)
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
            val processors = createSamplingSignalProcessors(blackListWithRegexWithOneChar)
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
                val processors = createSamplingSignalProcessors(blackListAllDenyConfig)
                val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

                sampledSpanExporter.export(listOf(createSpanData("test-span", emptyMap())))

                assertThat(spanExporter.finishedSpanItems).isEmpty()
            }

            @Test
            fun `in span, filters the span with a prop`() {
                val processors = createSamplingSignalProcessors(blackListAllDenyConfig)
                val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

                sampledSpanExporter.export(listOf(createSpanData("test-span", mapOf("key1" to "value1"))))

                assertThat(spanExporter.finishedSpanItems).isEmpty()
            }

            @Test
            fun `in log, filers the log without a prop`() {
                val processors = createSamplingSignalProcessors(blackListAllDenyConfig)
                val sampledLogExporter = processors.SampledLogExporter(logExporter)
                val mockLogRecord = createLogRecordData("test-log", emptyMap())
                sampledLogExporter.export(listOf(mockLogRecord))

                assertThat(logExporter.finishedLogRecordItems).isEmpty()
            }

            @Test
            fun `in log, filers the log with a prop`() {
                val processors = createSamplingSignalProcessors(blackListAllDenyConfig)
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
                    props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1")),
                ),
            )
        private val attributesDroppingConfig = PulseSdkConfigFakeUtils.createFakeConfig(attributesToDrop = attributesToDrop)
        val attributesDroppingProcessors = createSamplingSignalProcessors(attributesDroppingConfig)
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

    @Nested
    inner class `With attributes to add` {
        private val attributesToAdd =
            listOf(
                PulseSdkConfigFakeUtils.createFakeAttributesToAddEntry(
                    values =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeAttributeValue(
                                name = "NewAddedKeyName",
                                value = "NewAddedValueOfThatKey",
                                type = PulseAttributeType.STRING,
                            ),
                        ),
                    matcher =
                        createFakeSignalMatchCondition(
                            name = "test-span",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("State", ".*Haryana.*")),
                        ),
                ),
            )
        private val attributesAddingConfig = PulseSdkConfigFakeUtils.createFakeConfig(attributesToAdd = attributesToAdd)
        val attributesAddingProcessors = createSamplingSignalProcessors(attributesAddingConfig)
        val attributesAddingSpanExporter = attributesAddingProcessors.SampledSpanExporter(spanExporter)
        val attributesAddingLogExporter = attributesAddingProcessors.SampledLogExporter(logExporter)

        @Test
        fun `in span, export adds attributes when attributes match add conditions`() {
            val mockSpan = createSpanData("test-span", mapOf("State" to "Haryana"))

            attributesAddingSpanExporter.export(listOf(mockSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("NewAddedKeyName", "NewAddedValueOfThatKey")
        }

        @Test
        fun `in span, export adds new attribute when condition matches`() {
            val mockSpan = createSpanData("test-span", mapOf("State" to "Haryana", "key2" to "value2"))

            attributesAddingSpanExporter.export(listOf(mockSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            val attributes = spanExporter.finishedSpanItems[0].attributes
            OpenTelemetryAssertions
                .assertThat(attributes)
                .containsEntry("NewAddedKeyName", "NewAddedValueOfThatKey")
            OpenTelemetryAssertions
                .assertThat(attributes)
                .containsEntry("key2", "value2")
        }

        @Test
        fun `in span, export does not add when no attributes match add conditions`() {
            val mockSpan = createSpanData("test-span", mapOf("otherKey" to "value1"))

            attributesAddingSpanExporter.export(listOf(mockSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .doesNotContainKey("State")
        }

        @Test
        fun `in span, export does not add when value doesn't match but key match`() {
            val mockSpan = createSpanData("test-span", mapOf("State" to "Delhi"))

            attributesAddingSpanExporter.export(listOf(mockSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("State", "Delhi")
                .doesNotContainKey("NewAddedKeyName")
        }

        @Test
        fun `in span, export does not add when name doesn't match but value and key match`() {
            val mockSpan = createSpanData("test-span2", mapOf("State" to "Haryana"))

            attributesAddingSpanExporter.export(listOf(mockSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("test-span2")
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry("State", "Haryana")
                .doesNotContainKey("NewAddedKeyName")
        }

        @Test
        fun `in log, export adds attributes when attributes match add conditions`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("State" to "Haryana"))

            attributesAddingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("NewAddedKeyName", "NewAddedValueOfThatKey")
        }

        @Test
        fun `in log, export adds new attribute when condition matches`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("State" to "Haryana", "key2" to "value2"))

            attributesAddingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            val attributes = logExporter.finishedLogRecordItems[0].attributes
            OpenTelemetryAssertions
                .assertThat(attributes)
                .containsEntry("NewAddedKeyName", "NewAddedValueOfThatKey")
            OpenTelemetryAssertions
                .assertThat(attributes)
                .containsEntry("key2", "value2")
        }

        @Test
        fun `in log, export does not add when no attributes match add conditions`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("otherKey" to "value1"))

            attributesAddingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .doesNotContainKey("State")
        }

        @Test
        fun `in log, export does not add when value doesn't match but key match`() {
            val sampleLogRecord = createLogRecordData("test-span", mapOf("State" to "Delhi"))

            attributesAddingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("State", "Delhi")
                .doesNotContainKey("NewAddedKeyName")
        }

        @Test
        fun `in log, export does not add when name doesn't match but value and key match`() {
            val sampleLogRecord = createLogRecordData("test-span2", mapOf("State" to "Haryana"))

            attributesAddingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span2")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("State", "Haryana")
                .doesNotContainKey("NewAddedKeyName")
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

    @Nested
    inner class `Session scenarios` {
        @Test
        fun `in span, no data is sent when session sampling is off`() {
            val processors =
                createSamplingSignalProcessors(
                    config = whitelistAllAllowedConfig,
                    sessionParser = PulseSessionParser.alwaysOff,
                )
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
            val testSpan = createSpanData("test-span", emptyMap())

            sampledSpanExporter.export(listOf(testSpan))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }

        @RepeatedTest(10)
        fun `in span, all data is sent when session sampling is on`() {
            val processors =
                createSamplingSignalProcessors(
                    config = whitelistAllAllowedConfig,
                    sessionParser = PulseSessionParser.alwaysOn,
                )
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
            val testSpan = createSpanData("test-span", emptyMap())

            sampledSpanExporter.export(listOf(testSpan))

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
        }

        @RepeatedTest(10)
        fun `in span, data is sent when session random value less than session sampling config`() {
            val samplingRate = 0.5f
            val randomValue = 0.3f
            val config = PulseSdkConfigFakeUtils.createFakeConfig(sessionSampleRate = samplingRate)
            val mockRandom = createMockRandomGenerator(randomValue)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = mockRandom,
                )
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
            val testSpan = createSpanData("test-span", emptyMap())

            sampledSpanExporter.export(listOf(testSpan))

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
        }

        @RepeatedTest(10)
        fun `in span, data is sent when session random value is equal to session sampling config`() {
            val samplingRate = 0.5f
            val randomValue = 0.5f
            val config = PulseSdkConfigFakeUtils.createFakeConfig(sessionSampleRate = samplingRate)
            val mockRandom = createMockRandomGenerator(randomValue)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = mockRandom,
                )
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
            val testSpan = createSpanData("test-span", emptyMap())

            sampledSpanExporter.export(listOf(testSpan))

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            assertThat(spanExporter.finishedSpanItems[0].name).isEqualTo("test-span")
        }

        @RepeatedTest(10)
        fun `in span, data is not sent when session random value greater than session sampling config`() {
            val samplingRate = 0.5f
            val randomValue = 0.7f
            val config = PulseSdkConfigFakeUtils.createFakeConfig(sessionSampleRate = samplingRate)
            val mockRandom = createMockRandomGenerator(randomValue)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = mockRandom,
                )
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
            val testSpan = createSpanData("test-span", emptyMap())

            sampledSpanExporter.export(listOf(testSpan))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }

        @Test
        fun `in log, no data is sent when session sampling is off`() {
            val config = PulseSdkConfigFakeUtils.createFakeConfig()
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser.alwaysOff,
                )
            val sampledLogExporter = processors.SampledLogExporter(logExporter)
            val testLog = createLogRecordData("test-log", emptyMap())

            sampledLogExporter.export(listOf(testLog))

            assertThat(logExporter.finishedLogRecordItems).isEmpty()
        }

        @RepeatedTest(10)
        fun `in log, all data is sent when session sampling is on`() {
            val config = PulseSdkConfigFakeUtils.createFakeConfig()
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser.alwaysOn,
                )
            val sampledLogExporter = processors.SampledLogExporter(logExporter)
            val testLog = createLogRecordData("test-log", emptyMap())

            sampledLogExporter.export(listOf(testLog))

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
        }

        @RepeatedTest(10)
        fun `in log, data is sent when session random value less than session sampling config`() {
            val samplingRate = 0.5f
            val randomValue = 0.3f
            val config = PulseSdkConfigFakeUtils.createFakeConfig(sessionSampleRate = samplingRate)
            val mockRandom = createMockRandomGenerator(randomValue)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = mockRandom,
                )
            val sampledLogExporter = processors.SampledLogExporter(logExporter)
            val testLog = createLogRecordData("test-log", emptyMap())

            sampledLogExporter.export(listOf(testLog))

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
        }

        @RepeatedTest(10)
        fun `in log, data is sent when session random value is equal to session sampling config`() {
            val samplingRate = 0.5f
            val randomValue = 0.5f
            val config = PulseSdkConfigFakeUtils.createFakeConfig(sessionSampleRate = samplingRate)
            val mockRandom = createMockRandomGenerator(randomValue)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = mockRandom,
                )
            val sampledLogExporter = processors.SampledLogExporter(logExporter)
            val testLog = createLogRecordData("test-log", emptyMap())

            sampledLogExporter.export(listOf(testLog))

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
        }

        @RepeatedTest(10)
        fun `in log, data is not sent when session random value greater than session sampling config`() {
            val samplingRate = 0.5f
            val randomValue = 0.7f
            val config = PulseSdkConfigFakeUtils.createFakeConfig(sessionSampleRate = samplingRate)
            val mockRandom = createMockRandomGenerator(randomValue)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = mockRandom,
                )
            val sampledLogExporter = processors.SampledLogExporter(logExporter)
            val testLog = createLogRecordData("test-log", emptyMap())

            sampledLogExporter.export(listOf(testLog))

            assertThat(logExporter.finishedLogRecordItems).isEmpty()
        }
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

    private fun createSamplingSignalProcessors(
        config: PulseSdkConfig,
        signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
        sessionParser: PulseSessionParser = PulseSessionParser.alwaysOn,
        randomIdGenerator: Random = SecureRandom(),
        currentSdkName: PulseSdkName = PulseSdkName.ANDROID_JAVA,
    ): PulseSamplingSignalProcessors {
        val context = mockk<Context>()
        return PulseSamplingSignalProcessors(
            context = context,
            sdkConfig = config,
            currentSdkName = currentSdkName,
            signalMatcher = signalMatcher,
            sessionParser = sessionParser,
            randomIdGenerator = randomIdGenerator,
        )
    }

    private fun createMockRandomGenerator(value: Float): Random =
        mockk<Random>().apply {
            every { nextFloat() } returns value
        }
}
