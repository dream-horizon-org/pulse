@file:Suppress("DEPRECATION", "ClassName")

package com.pulse.sampling.core

import android.content.Context
import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.models.PulseAttributeType
import com.pulse.sampling.models.PulseCriticalEventPolicies
import com.pulse.sampling.models.PulseMetricsToAddEntry
import com.pulse.sampling.models.PulseMetricsToAddTarget
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.PulseSignalsToSampleEntry
import com.pulse.sampling.models.SamplingRate
import com.pulse.utils.createLogRecordData
import com.pulse.utils.createMetricData
import com.pulse.utils.createSpanData
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.assertj.DoublePointAssert
import io.opentelemetry.sdk.testing.assertj.DoubleSumAssert
import io.opentelemetry.sdk.testing.assertj.LongPointAssert
import io.opentelemetry.sdk.testing.assertj.LongSumAssert
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                            "abc.",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1")),
                        ),
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
                signalFilters = listOf(PulseSdkConfigFakeUtils.createFakeSignalMatchCondition("abc.")),
            )

        val blackListWithRegexWithOneCharAndProp =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.BLACKLIST,
                signalFilters =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                            "abc.",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1")),
                        ),
                    ),
            )

        val blackListWithRegexWithOneCharAndPropRegex =
            PulseSdkConfigFakeUtils.createFakeConfig(
                filterMode = PulseSignalFilterMode.BLACKLIST,
                signalFilters =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                            "abc.",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1.")),
                        ),
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
                PulseSdkConfigFakeUtils.createFakeAttributesToDropEntry(
                    values = listOf("key1"),
                    condition =
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                            name = "test-span",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1")),
                        ),
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
                .containsKey("key2")
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

        @Test
        fun `in log, export does filter keys matching regexes present in values array`() {
            val attributesToDrop =
                listOf(
                    PulseSdkConfigFakeUtils.createFakeAttributesToDropEntry(
                        values =
                            listOf(
                                "keyD\\d",
                                "nonce",
                            ),
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "test-span\\d",
                                props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key1", "value1")),
                            ),
                    ),
                )
            val attributesDroppingConfig = PulseSdkConfigFakeUtils.createFakeConfig(attributesToDrop = attributesToDrop)
            val attributesDroppingProcessors = createSamplingSignalProcessors(attributesDroppingConfig)
            val attributesDroppingLogExporter = attributesDroppingProcessors.SampledLogExporter(logExporter)

            val sampleLogRecord =
                createLogRecordData(
                    "test-span2",
                    mapOf(
                        "key1" to "value1",
                        "keyD1" to "value1",
                        "keyD2" to "value1",
                        "keyD3" to "value1",
                        "nonce" to "value1",
                        "other" to "value1",
                    ),
                )

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span2")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("key1", "value1")
                .containsEntry("other", "value1")
                .doesNotContainKey("keyD1")
                .doesNotContainKey("keyD2")
                .doesNotContainKey("keyD3")
                .doesNotContainKey("nonce")
        }

        @Test
        fun `in log, export does filter keys matching regexes present in values array where condition prop value also contain regex`() {
            val attributesToDrop =
                listOf(
                    PulseSdkConfigFakeUtils.createFakeAttributesToDropEntry(
                        values =
                            listOf(
                                "keyD\\d",
                                "nonce",
                            ),
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "test-span\\d",
                                // here value is regex
                                props = setOf(PulseSdkConfigFakeUtils.createFakeProp("key\\d", "value\\d")),
                            ),
                    ),
                )
            val attributesDroppingConfig = PulseSdkConfigFakeUtils.createFakeConfig(attributesToDrop = attributesToDrop)
            val attributesDroppingProcessors = createSamplingSignalProcessors(attributesDroppingConfig)
            val attributesDroppingLogExporter = attributesDroppingProcessors.SampledLogExporter(logExporter)

            val sampleLogRecord =
                createLogRecordData(
                    "test-span2",
                    mapOf(
                        "key1" to "value1",
                        "key12" to "value1", // will not be dropped as key12 doesn't match the key regex
                        "key3" to "value12", // will not be dropped as value12 doesn't match the value regex
                        "keyD1" to "value1",
                        "keyD2" to "value1",
                        "keyD3" to "value1",
                        "nonce" to "value1",
                        "other" to "value1",
                    ),
                )

            attributesDroppingLogExporter.export(listOf(sampleLogRecord))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
                .first()
                .extracting { it.bodyValue!!.asString() }
                .isEqualTo("test-span2")
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry("key1", "value1")
                .containsEntry("other", "value1")
                .containsEntry("key12", "value1")
                .containsEntry("key3", "value12")
                .doesNotContainKey("keyD1")
                .doesNotContainKey("keyD2")
                .doesNotContainKey("keyD3")
                .doesNotContainKey("nonce")
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
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
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

    @Nested
    inner class `Metrics to add` {
        private val metricReader = InMemoryMetricReader.create()
        private val meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build()

        private fun createSampledSpanExporter(
            metricsToAdd: List<PulseMetricsToAddEntry>,
        ): PulseSamplingSignalProcessors.SampledSpanExporter {
            val config = PulseSdkConfigFakeUtils.createFakeConfig(metricsToAdd = metricsToAdd)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    meterProviderForMetricsToAdd = meterProvider,
                )
            return processors.SampledSpanExporter(spanExporter)
        }

        private fun createSampledLogExporter(metricsToAdd: List<PulseMetricsToAddEntry>): PulseSamplingSignalProcessors.SampledLogExporter {
            val config = PulseSdkConfigFakeUtils.createFakeConfig(metricsToAdd = metricsToAdd)
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    meterProviderForMetricsToAdd = meterProvider,
                )
            return processors.SampledLogExporter(logExporter)
        }

        @Nested
        inner class Counter {
            private val metricData = PulseSdkConfigFakeUtils.createFakeCounter()

            @Test
            fun `in span, records when condition matches and target is Name`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "span_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(
                    listOf(
                        createSpanData("1", emptyMap()),
                        createSpanData("2", emptyMap()),
                    ),
                )
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("span_count")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(2L) }) }
            }

            @Test
            fun `in span, does not record when condition does not match`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "span_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = "exact_match_only",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(listOf(createSpanData("1", emptyMap())))
                sampledSpanExporter.export(listOf(createSpanData("2", emptyMap())))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).isEmpty()
            }

            @Test
            fun `in log, records when condition matches and target is Name`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "log_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.LOGS),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledLogExporter = createSampledLogExporter(metricsToAdd)
                sampledLogExporter.export(listOf(createLogRecordData("1", emptyMap())))
                sampledLogExporter.export(listOf(createLogRecordData("2", emptyMap())))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("log_count")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(2L) }) }
            }

            @Test
            fun `in span, records when target is Attribute and attribute key matches`() {
                val attributeMatcher =
                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                        name = ".*",
                        props = setOf(PulseSdkConfigFakeUtils.createFakeProp("attr_key", ".*")),
                        scopes = setOf(PulseSignalScope.TRACES),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "1",
                            target = PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(condition = attributeMatcher),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(listOf(createSpanData("any_span", mapOf("attr_key" to "2"))))
                sampledSpanExporter.export(listOf(createSpanData("any_span", mapOf("attr_key" to "3"))))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("m1")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(2L) }) }
            }

            @Test
            fun `in span, records when target is Attribute and attribute key matches multiple attr in same signal`() {
                val attributeMatcher =
                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                        name = ".*",
                        props = setOf(PulseSdkConfigFakeUtils.createFakeProp("attr_key_.", ".*")),
                        scopes = setOf(PulseSignalScope.TRACES),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "1",
                            target = PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(condition = attributeMatcher),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(
                    listOf(
                        createSpanData(
                            "any_span_1",
                            mapOf(
                                "attr_key_1" to "2",
                                "attr_key_2" to "3",
                            ),
                        ),
                    ),
                )
                sampledSpanExporter.export(listOf(createSpanData("any_span_2", mapOf("attr_key_3" to "4"))))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("m1")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(3L) }) }
            }

            @Test
            fun `in span, has 0 when no spans exported`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "span_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(emptyList())
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).isEmpty()
            }

            @Test
            fun `in span, does not record when condition matches and target is Name but doesn't match sdk`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "span_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                    sdks = setOf(PulseSdkName.ANDROID_RN),
                                ),
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(
                    listOf(
                        createSpanData("1", emptyMap()),
                        createSpanData("2", emptyMap()),
                    ),
                )
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).isEmpty()
            }

            @Test
            fun `in span, records when attribute condition matches both key and value`() {
                val attributeMatcher =
                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                        name = ".*",
                        props = setOf(PulseSdkConfigFakeUtils.createFakeProp("status_\\d", "error\\d+")),
                        scopes = setOf(PulseSignalScope.TRACES),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "error_span_count",
                            target = PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(condition = attributeMatcher),
                            condition = attributeMatcher,
                            type = metricData,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(listOf(createSpanData("any_span", mapOf("status_0" to "error00"))))
                sampledSpanExporter.export(listOf(createSpanData("any_span", mapOf("status_1" to "error01"))))
                sampledSpanExporter.export(listOf(createSpanData("other_span", mapOf("status" to "ok"))))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("error_span_count")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(2L) }) }
            }
        }

        @Nested
        inner class `Attributes To Pick` {
            private val metricData = PulseSdkConfigFakeUtils.createFakeCounter()

            private val attributesToPick =
                listOf(
                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                        name = ".*",
                        props =
                            setOf(
                                PulseSdkConfigFakeUtils.createFakeProp("env", null),
                                PulseSdkConfigFakeUtils.createFakeProp("region", null),
                            ),
                    ),
                )
            private val expectedAttrs =
                Attributes.of(
                    AttributeKey.stringKey("env"),
                    "prod",
                    AttributeKey.stringKey("region"),
                    "eu-west",
                )

            @Test
            fun `in span, emitted metric carries only picked attributes`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "span_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = metricData,
                            attributesToPick = attributesToPick,
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(
                    listOf(
                        createSpanData(
                            "api_span",
                            mapOf(
                                "env" to "prod",
                                "region" to "eu-west",
                                "internal_key" to "should_not_appear",
                            ),
                        ),
                    ),
                )
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("span_count")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert ->
                        sum.hasPointsSatisfying(
                            { pt: LongPointAssert -> pt.hasValue(1L).hasAttributes(expectedAttrs) },
                        )
                    }
            }

            @Test
            fun `in log, emitted metric carries only picked attributes`() {
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "log_count",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.LOGS),
                                ),
                            type = metricData,
                            attributesToPick = attributesToPick,
                        ),
                    )
                val sampledLogExporter = createSampledLogExporter(metricsToAdd)
                sampledLogExporter.export(
                    listOf(
                        createLogRecordData(
                            "error_log",
                            mapOf(
                                "env" to "prod",
                                "region" to "eu-west",
                                "internal_key" to "should_not_appear",
                            ),
                        ),
                    ),
                )
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("log_count")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert ->
                        sum.hasPointsSatisfying(
                            { pt: LongPointAssert -> pt.hasValue(1L).hasAttributes(expectedAttrs) },
                        )
                    }
            }
        }

        @Nested
        inner class Sum {
            @Nested
            inner class `without fraction` {
                private val metricData = PulseSdkConfigFakeUtils.createFakeSum(isFraction = false)

                @Test
                fun `in span, records long non-monotonic sum when condition matches`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = metricData,
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(
                        listOf(
                            createSpanData("10", emptyMap()),
                            createSpanData("2", emptyMap()),
                        ),
                    )
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_sum")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(12L) }) }
                }

                @Test
                fun `in span, records when target is Attribute and attribute key matches`() {
                    val attributeMatcher =
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                            name = ".*",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("attr_key", ".*")),
                            scopes = setOf(PulseSignalScope.TRACES),
                        )
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "1",
                                target = PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(condition = attributeMatcher),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = metricData,
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("any_span", mapOf("attr_key" to "2"))))
                    sampledSpanExporter.export(listOf(createSpanData("any_span", mapOf("attr_key" to "3"))))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("m1")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(5L) }) }
                }

                @Test
                fun `in span, records when target is Attribute and attribute key matches multiple attr in same signal`() {
                    val attributeMatcher =
                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                            name = ".*",
                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("attr_key_.", "\\d")),
                            scopes = setOf(PulseSignalScope.TRACES),
                        )
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "1",
                                target = PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(condition = attributeMatcher),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = metricData,
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(
                        listOf(
                            createSpanData(
                                "any_span_1",
                                mapOf(
                                    "attr_key_1" to "2",
                                    "attr_key_2" to "3",
                                    "attr_key_3" to "30", // doesn't match /d which is single digit
                                ),
                            ),
                        ),
                    )
                    sampledSpanExporter.export(listOf(createSpanData("any_span_2", mapOf("attr_key_4" to "4"))))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("m1")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(9L) }) }
                }

                @Test
                fun `in span, records long monotonic sum when isMonotonic is true`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum_monotonic",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = false, isMonotonic = true),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("5", emptyMap())))
                    sampledSpanExporter.export(listOf(createSpanData("7", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_sum_monotonic")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(12L) }) }
                }

                @Test
                fun `in span, records long non monotonic sum when isMonotonic is false`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum_monotonic",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = false, isMonotonic = false),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("5", emptyMap())))
                    sampledSpanExporter.export(listOf(createSpanData("-7", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_sum_monotonic")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(-2L) }) }
                }
            }

            @Nested
            inner class `with fraction` {
                private val metricData = PulseSdkConfigFakeUtils.createFakeSum(isFraction = true)

                @Test
                fun `in span, records double up-down sum when condition matches`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum_double",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = metricData,
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("2.5", emptyMap())))
                    sampledSpanExporter.export(listOf(createSpanData("3.2", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_sum_double")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasDoubleSumSatisfying { sum: DoubleSumAssert ->
                            sum.hasPointsSatisfying(
                                { pt: DoublePointAssert -> pt.hasValue(5.7) },
                            )
                        }
                }

                @Test
                fun `in span, does not record double up-down sum when condition matches but data format is wrong`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum_double",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = metricData,
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("2.f5", emptyMap())))
                    sampledSpanExporter.export(listOf(createSpanData("3.f2", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).isEmpty()
                }

                @Test
                fun `in span, records double monotonic sum when isMonotonic is true`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum_double_monotonic",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = true, isMonotonic = true),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("1.5", emptyMap())))
                    sampledSpanExporter.export(listOf(createSpanData("2.7", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_sum_double_monotonic")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasDoubleSumSatisfying { sum: DoubleSumAssert ->
                            sum.hasPointsSatisfying(
                                { pt: DoublePointAssert -> assertThat(pt.actual().value).isCloseTo(4.2, offset(0.0001)) },
                            )
                        }
                }

                @Test
                fun `in span, records double monotonic sum when isMonotonic is false`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_sum_double_monotonic",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = true, isMonotonic = false),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("1.5", emptyMap())))
                    sampledSpanExporter.export(listOf(createSpanData("-2.7", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_sum_double_monotonic")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasDoubleSumSatisfying { sum: DoubleSumAssert ->
                            sum.hasPointsSatisfying(
                                { pt: DoublePointAssert -> assertThat(pt.actual().value).isCloseTo(-1.2, offset(0.0001)) },
                            )
                        }
                }
            }
        }

        @Nested
        inner class Histogram {
            @Nested
            inner class `without fraction` {
                @Test
                fun `in span, records with more than one bucket entry when multiple values recorded`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_histogram",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type =
                                    PulseSdkConfigFakeUtils.createFakeHistogram(
                                        bucket = listOf(1, 5.0, 10),
                                        isFraction = false,
                                    ),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(
                        listOf(
                            createSpanData("2", emptyMap()),
                            createSpanData("7", emptyMap()),
                            createSpanData("12", emptyMap()),
                        ),
                    )
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_histogram")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasHistogramSatisfying { histogram ->
                            histogram.hasPointsSatisfying(
                                { pt ->
                                    pt.hasCount(3)
                                    pt.hasSum(21.0)
                                    pt.hasBucketCounts(0, 1, 1, 1)
                                },
                            )
                        }
                }
            }

            @Nested
            inner class `with fraction` {
                @Test
                fun `in span, records double histogram when multiple values recorded`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_histogram_double",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type =
                                    PulseSdkConfigFakeUtils.createFakeHistogram(
                                        bucket = listOf(0.5, 2.0, 5.0),
                                        isFraction = true,
                                    ),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(
                        listOf(
                            createSpanData("1.0", emptyMap()),
                            createSpanData("3.0", emptyMap()),
                        ),
                    )
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_histogram_double")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasHistogramSatisfying { histogram ->
                            histogram.hasPointsSatisfying(
                                { pt ->
                                    pt.hasCount(2)
                                    pt.hasSum(4.0)
                                    pt.hasBucketCounts(0, 1, 1, 0)
                                },
                            )
                        }
                }

                @Test
                fun `in span, records double histogram when buckets are not set`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_histogram_no_buckets",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeHistogram(isFraction = true),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(
                        listOf(
                            createSpanData("1.5", emptyMap()),
                            createSpanData("2.5", emptyMap()),
                        ),
                    )
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_histogram_no_buckets")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasHistogramSatisfying { histogram ->
                            histogram.hasPointsSatisfying(
                                { pt ->
                                    pt.hasCount(2)
                                    pt.hasSum(4.0)
                                    pt.hasBucketCounts(0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                                },
                            )
                        }
                }
            }
        }

        @Nested
        inner class Gauge {
            @Nested
            inner class `without fraction` {
                @Test
                fun `in span, records long gauge when condition matches`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_gauge",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeGauge(isFraction = false),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("42", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_gauge")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongGaugeSatisfying { gauge ->
                            gauge.hasPointsSatisfying(
                                { pt: LongPointAssert ->
                                    pt.hasValue(42L)
                                },
                            )
                        }
                }

                @Test
                fun `in span, records long gauge when condition matches and session sampling is off`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_gauge",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeGauge(isFraction = false),
                            ),
                        )
                    val config = PulseSdkConfigFakeUtils.createFakeConfig(metricsToAdd = metricsToAdd)
                    val processors =
                        createSamplingSignalProcessors(
                            config = config,
                            // session sampling is off
                            sessionParser = PulseSessionParser.alwaysOff,
                            meterProviderForMetricsToAdd = meterProvider,
                        )
                    val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
                    sampledSpanExporter.export(listOf(createSpanData("42", emptyMap())))
                    meterProvider.forceFlush()

                    assertThat(spanExporter.finishedSpanItems).isEmpty()
                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_gauge")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasLongGaugeSatisfying { gauge ->
                            gauge.hasPointsSatisfying(
                                { pt: LongPointAssert ->
                                    pt.hasValue(42L)
                                },
                            )
                        }
                }

                @Test
                fun `in span, does not record long gauge when condition matches but scope is wrong`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_gauge",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.LOGS),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeGauge(isFraction = false),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("42", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).isEmpty()
                }
            }

            @Nested
            inner class `with fraction` {
                @Test
                fun `in span, records double gauge when condition matches`() {
                    val metricsToAdd =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                                name = "span_gauge_double",
                                target = PulseMetricsToAddTarget.Name(type = "name"),
                                condition =
                                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                        name = ".*",
                                        scopes = setOf(PulseSignalScope.TRACES),
                                    ),
                                type = PulseSdkConfigFakeUtils.createFakeGauge(isFraction = true),
                            ),
                        )
                    val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                    sampledSpanExporter.export(listOf(createSpanData("7.25", emptyMap())))
                    meterProvider.forceFlush()

                    val metrics = metricReader.collectAllMetrics().toList()
                    assertThat(metrics).hasSize(1)
                    assertThat(metrics[0].name).isEqualTo("span_gauge_double")
                    OpenTelemetryAssertions
                        .assertThat(metrics[0])
                        .hasDoubleGaugeSatisfying { gauge ->
                            gauge.hasPointsSatisfying(
                                { pt: DoublePointAssert ->
                                    pt.hasValue(
                                        7.25,
                                    )
                                },
                            )
                        }
                }
            }
        }

        @Nested
        inner class `Add prop name as suffix` {
            @Test
            fun `in span, metric name has attribute key as suffix when addPropNameAsSuffix is true`() {
                val attributeMatcher =
                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                        name = ".*",
                        props = setOf(PulseSdkConfigFakeUtils.createFakeProp("response_time_ms", ".*")),
                        scopes = setOf(PulseSignalScope.TRACES),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "api_time",
                            target =
                                PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(
                                    condition = attributeMatcher,
                                    shouldAddPropNameAsSuffix = true,
                                ),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = false),
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(listOf(createSpanData("my_span", mapOf("response_time_ms" to "250"))))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("api_time.response_time_ms")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(250L) }) }
            }

            @Test
            fun `in span, metric name has no suffix when addPropNameAsSuffix is false`() {
                val attributeMatcher =
                    PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                        name = ".*",
                        props = setOf(PulseSdkConfigFakeUtils.createFakeProp("response_time_ms", ".*")),
                        scopes = setOf(PulseSignalScope.TRACES),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "api_time",
                            target =
                                PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(
                                    condition = attributeMatcher,
                                    shouldAddPropNameAsSuffix = false,
                                ),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = ".*",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = false),
                        ),
                    )
                val sampledSpanExporter = createSampledSpanExporter(metricsToAdd)
                sampledSpanExporter.export(listOf(createSpanData("my_span", mapOf("response_time_ms" to "250"))))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("api_time")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(250L) }) }
            }
        }

        @Nested
        inner class `Metrics observe added attributes` {
            @Test
            fun `in span, metric condition matches attribute added by attributesToAdd`() {
                val attributesToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeAttributesToAddEntry(
                            values =
                                listOf(
                                    PulseSdkConfigFakeUtils.createFakeAttributeValue(
                                        name = "enriched_key",
                                        value = "enriched_val",
                                        type = PulseAttributeType.STRING,
                                    ),
                                ),
                            matcher =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = "my_span",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                        ),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "enriched_metric",
                            target = PulseMetricsToAddTarget.Name(type = "name"),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = "my_span",
                                    props = setOf(PulseSdkConfigFakeUtils.createFakeProp("enriched_key", ".*")),
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = PulseSdkConfigFakeUtils.createFakeCounter(),
                        ),
                    )
                val config =
                    PulseSdkConfigFakeUtils.createFakeConfig(
                        attributesToAdd = attributesToAdd,
                        metricsToAdd = metricsToAdd,
                    )
                val processors =
                    createSamplingSignalProcessors(
                        config = config,
                        meterProviderForMetricsToAdd = meterProvider,
                    )
                val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
                sampledSpanExporter.export(listOf(createSpanData("my_span", emptyMap())))
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("enriched_metric")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(1L) }) }
            }

            @Test
            fun `in span, metric condition derives metrics from attributes added by attributesToAdd`() {
                val attributesToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeAttributesToAddEntry(
                            values =
                                listOf(
                                    PulseSdkConfigFakeUtils.createFakeAttributeValue(
                                        name = "response_time_ms",
                                        value = "350",
                                        type = PulseAttributeType.LONG,
                                    ),
                                ),
                            matcher =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = "api_span",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                        ),
                    )
                val metricsToAdd =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeMetricsToAddEntry(
                            name = "api_latency",
                            target =
                                PulseSdkConfigFakeUtils.createFakeMetricsToAddTargetAttribute(
                                    condition =
                                        PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                            name = ".*",
                                            props = setOf(PulseSdkConfigFakeUtils.createFakeProp("response_time_ms", ".*")),
                                            scopes = setOf(PulseSignalScope.TRACES),
                                        ),
                                ),
                            condition =
                                PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                    name = "api_span",
                                    scopes = setOf(PulseSignalScope.TRACES),
                                ),
                            type = PulseSdkConfigFakeUtils.createFakeSum(isFraction = false),
                        ),
                    )
                val config =
                    PulseSdkConfigFakeUtils.createFakeConfig(
                        attributesToAdd = attributesToAdd,
                        metricsToAdd = metricsToAdd,
                    )
                val processors =
                    createSamplingSignalProcessors(
                        config = config,
                        meterProviderForMetricsToAdd = meterProvider,
                    )
                val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)
                // span has no "response_time_ms" originally — it gets added by attributesToAdd
                sampledSpanExporter.export(
                    listOf(
                        createSpanData("api_span", emptyMap()),
                        createSpanData("api_span", emptyMap()),
                    ),
                )
                meterProvider.forceFlush()

                val metrics = metricReader.collectAllMetrics().toList()
                assertThat(metrics).hasSize(1)
                assertThat(metrics[0].name).isEqualTo("api_latency")
                OpenTelemetryAssertions
                    .assertThat(metrics[0])
                    .hasLongSumSatisfying { sum: LongSumAssert -> sum.hasPointsSatisfying({ pt: LongPointAssert -> pt.hasValue(700L) }) }
            }
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

        @Test
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

        @Test
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

        @Test
        fun `in span, data is sent when session random value is equal to session sampling config`() {
            val samplingRate = 0.51f
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

        @Test
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
        fun `in span, data is not sent when session random value is 0_0 and random is 0_0`() {
            val samplingRate = 0.0f
            val randomValue = 0.0f
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

        @Test
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

        @Test
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

        @Test
        fun `in log, data is sent when session random value is greater than session sampling config`() {
            val samplingRate = 0.501f
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

        @Test
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

        @ParameterizedTest(name = "in metric, session sampling: rate={0}, random={1} -> exported {2}")
        @CsvSource(
            "0.501, 0.5, 1",
            "0.5, 0.7, 0",
        )
        fun `in metric, data is sent or not sent per session random vs session sampling config`(
            samplingRateStr: String,
            randomValueStr: String,
            expectedExportedCountStr: String,
        ) {
            val samplingRate = samplingRateStr.toFloat()
            val randomValue = randomValueStr.toFloat()
            val expectedExportedCount = expectedExportedCountStr.toInt()
            val config =
                PulseSdkConfigFakeUtils.createFakeConfig(
                    sessionSampleRate = samplingRate,
                    signalFilters =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                scopes = setOf(PulseSignalScope.METRICS),
                            ),
                        ),
                )
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = PulseSessionParser { _, _, _ -> samplingRate },
                    randomIdGenerator = createMockRandomGenerator(randomValue),
                )
            val exporter = processors.SampledMetricExporter(metricExporter)
            val testSignal = createMetricData("test-signal", emptyMap())

            exporter.export(listOf(testSignal))

            assertThat(metricExporter.finishedMetricItems).hasSize(expectedExportedCount)
            if (expectedExportedCount == 1) {
                assertThat(metricExporter.finishedMetricItems[0].name).isEqualTo("test-signal")
            }
        }
    }

    @Nested
    inner class `Critical Event Policy` {
        @Test
        fun `in log, crash event matches critical event policy by name and is always sent when session sampling is off`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "device\\.crash",
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val crashLog = createLogRecordData("device.crash", emptyMap())
            sampledLogExporter.export(listOf(crashLog))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
            assertThat(
                logExporter.finishedLogRecordItems
                    .first()
                    .bodyValue
                    ?.asString(),
            ).isEqualTo("device.crash")
        }

        @Test
        fun `in log, crash event with matching property matches critical event policy and is always sent`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "device\\.crash",
                                props = setOf(PulseSdkConfigFakeUtils.createFakeProp("severity", "critical")),
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val crashLog =
                createLogRecordData(
                    "device.crash",
                    mapOf("severity" to "critical"),
                )
            sampledLogExporter.export(listOf(crashLog))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
            assertThat(
                logExporter.finishedLogRecordItems
                    .first()
                    .bodyValue
                    ?.asString(),
            ).isEqualTo("device.crash")
        }

        @Test
        fun `in log, crash event with non-matching property does not match critical event policy and is filtered out`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "device\\.crash",
                                props = setOf(PulseSdkConfigFakeUtils.createFakeProp("severity", "critical")),
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val crashLog =
                createLogRecordData(
                    "device.crash",
                    mapOf("severity" to "non-critical"),
                )
            sampledLogExporter.export(listOf(crashLog))

            assertThat(logExporter.finishedLogRecordItems)
                .isEmpty()
        }

        @Test
        fun `in log, non-critical event does not match critical event policy and is filtered out when session sampling is off`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "device\\.crash",
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val regularLog = createLogRecordData("app.widget.click", emptyMap())
            sampledLogExporter.export(listOf(regularLog))

            assertThat(logExporter.finishedLogRecordItems)
                .isEmpty()
        }

        @Test
        fun `in span, critical span matches critical event policy by name and is always sent when session sampling is off`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "critical\\.error",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            val criticalSpan = createSpanData("critical.error", emptyMap())
            sampledSpanExporter.export(listOf(criticalSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("critical.error")
        }

        @Test
        fun `in span, critical span with matching property matches critical event policy and is always sent`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "error\\.span",
                                props = setOf(PulseSdkConfigFakeUtils.createFakeProp("error.type", "fatal")),
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            val errorSpan =
                createSpanData(
                    "error.span",
                    mapOf("error.type" to "fatal"),
                )
            sampledSpanExporter.export(listOf(errorSpan))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("error.span")
        }

        @Test
        fun `in span, span with non-matching scope does not match critical event policy and is filtered out`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "critical\\.span",
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledSpanExporter = processors.SampledSpanExporter(spanExporter)

            val span = createSpanData("critical.span", emptyMap())
            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems)
                .isEmpty()
        }

        @Test
        fun `in log, when critical event policy is null all events are filtered out when session sampling is off`() {
            val config = createConfigWithCriticalEventPolicy(null)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val crashLog = createLogRecordData("device.crash", emptyMap())
            sampledLogExporter.export(listOf(crashLog))

            assertThat(logExporter.finishedLogRecordItems)
                .isEmpty()
        }

        @Test
        fun `in log, when critical event policy is empty all events are filtered out when session sampling is off`() {
            val config =
                createConfigWithCriticalEventPolicy(
                    PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                        alwaysSend = emptyList(),
                    ),
                )
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOff)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val crashLog = createLogRecordData("device.crash", emptyMap())
            sampledLogExporter.export(listOf(crashLog))

            assertThat(logExporter.finishedLogRecordItems)
                .isEmpty()
        }

        @Test
        fun `in log, when session sampling is on all events are sent regardless of critical event policy`() {
            val criticalEventPolicy =
                PulseSdkConfigFakeUtils.createFakeCriticalEventPolicies(
                    alwaysSend =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "device\\.crash",
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        ),
                )
            val config = createConfigWithCriticalEventPolicy(criticalEventPolicy)
            val processors = createSamplingSignalProcessors(config, sessionParser = PulseSessionParser.alwaysOn)
            val sampledLogExporter = processors.SampledLogExporter(logExporter)

            val regularLog = createLogRecordData("app.widget.click", emptyMap())
            sampledLogExporter.export(listOf(regularLog))

            assertThat(logExporter.finishedLogRecordItems)
                .hasSize(1)
            assertThat(
                logExporter.finishedLogRecordItems
                    .first()
                    .bodyValue
                    ?.asString(),
            ).isEqualTo("app.widget.click")
        }

        private fun createConfigWithCriticalEventPolicy(criticalEventPolicy: PulseCriticalEventPolicies?): PulseSdkConfig {
            val samplingConfig =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    criticalEventPolicies = criticalEventPolicy,
                )
            return PulseSdkConfigFakeUtils.createFakeConfig(
                sampling = samplingConfig,
            )
        }
    }

    @Nested
    inner class `Signals To Sample` {
        private fun getProcessorsWithSignalToSample(
            vararg signalSampleConfig: PulseSignalsToSampleEntry,
            configSessionSampleRate: SamplingRate,
            random: Random = createMockRandomGenerator(1.0F),
        ): Pair<PulseSamplingSignalProcessors.SampledSpanExporter, PulseSamplingSignalProcessors.SampledLogExporter> {
            val samplingConfig =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    signalsToSample = signalSampleConfig.toList(),
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(configSessionSampleRate),
                )
            val config =
                PulseSdkConfigFakeUtils.createFakeConfig(
                    sampling = samplingConfig,
                )
            val processors =
                createSamplingSignalProcessors(
                    config = config,
                    sessionParser = { _, _, _ -> configSessionSampleRate },
                    randomIdGenerator = random,
                )
            return processors.SampledSpanExporter(spanExporter) to
                processors.SampledLogExporter(logExporter)
        }

        @Test
        fun `span matches condition with signal rate 1_0 session rate 0_0 and random 0_0`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "target\\.span",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 1.0f,
                    ),
                    configSessionSampleRate = 0.0F,
                    random = createMockRandomGenerator(0.0F),
                )
            val span = createSpanData("target.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("target.span")
        }

        @Test
        fun `span matches condition with signal rate 0_6 session rate 0_0 and random 0_5`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "target\\.span",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 0.6f,
                    ),
                    configSessionSampleRate = 0.0F,
                    random = createMockRandomGenerator(0.5F),
                )
            val span = createSpanData("target.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("target.span")
        }

        @Test
        fun `span matches condition with signal rate 0_4 session rate 0_0 and random 0_5`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "target\\.span",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 0.4f,
                    ),
                    configSessionSampleRate = 0.0F,
                    random = createMockRandomGenerator(0.5F),
                )
            val span = createSpanData("target.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }

        @Test
        fun `span matches condition with signal rate 0_0 session rate 1_0 and random 0_5`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "target\\.span",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 0.0f,
                    ),
                    configSessionSampleRate = 1F,
                    random = createMockRandomGenerator(0.5F),
                )
            val span = createSpanData("target.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }

        @Test
        fun `log matches condition with signal rate 0_0 session rate 1_0 and random 0_5`() {
            val (_, sampledLogExporter) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "target\\.log",
                                scopes = setOf(PulseSignalScope.LOGS),
                            ),
                        sampleRate = 0.0f,
                    ),
                    configSessionSampleRate = 1_0F,
                    random = createMockRandomGenerator(0.5F),
                )
            val log = createLogRecordData("target.log", emptyMap())

            sampledLogExporter.export(listOf(log))

            assertThat(logExporter.finishedLogRecordItems).isEmpty()
        }

        @Test
        fun `span does not match any condition with session rate 0_0 and random 0_5`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "other\\.signal",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 1.0f,
                    ),
                    configSessionSampleRate = 0.0F,
                    random = createMockRandomGenerator(0.5F),
                )
            val span = createSpanData("unmatched.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }

        @Test
        fun `span does not match any condition with session rate 1_0 and random 0_5`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "other\\.signal",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 0.0f,
                    ),
                    configSessionSampleRate = 1.0F,
                    random = createMockRandomGenerator(0.5F),
                )
            val span = createSpanData("unmatched.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems)
                .hasSize(1)
                .first()
                .extracting { it.name }
                .isEqualTo("unmatched.span")
        }

        @Test
        fun `span matches condition with signal rate 0_0 session rate 0_0 and random 0_0`() {
            val (sampledSpanExporter, _) =
                getProcessorsWithSignalToSample(
                    PulseSdkConfigFakeUtils.createFakeSignalsToSampleEntry(
                        condition =
                            PulseSdkConfigFakeUtils.createFakeSignalMatchCondition(
                                name = "target\\.span",
                                scopes = setOf(PulseSignalScope.TRACES),
                            ),
                        sampleRate = 0.0f,
                    ),
                    configSessionSampleRate = 1.0F,
                    random = createMockRandomGenerator(0.0F),
                )
            val span = createSpanData("target.span", emptyMap())

            sampledSpanExporter.export(listOf(span))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }
    }

    private fun createSamplingSignalProcessors(
        config: PulseSdkConfig,
        signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
        sessionParser: PulseSessionParser = PulseSessionParser.alwaysOn,
        randomIdGenerator: Random = SecureRandom(),
        currentSdkName: PulseSdkName = PulseSdkName.ANDROID_JAVA,
        meterProviderForMetricsToAdd: SdkMeterProvider = SdkMeterProvider.builder().build(),
    ): PulseSamplingSignalProcessors {
        val context = mockk<Context>()
        return PulseSamplingSignalProcessors(
            context = context,
            sdkConfig = config,
            currentSdkName = currentSdkName,
            signalMatcher = signalMatcher,
            sessionParser = sessionParser,
            randomIdGenerator = randomIdGenerator,
            meterProviderLazy = lazy { meterProviderForMetricsToAdd },
        )
    }

    private fun createMockRandomGenerator(value: Float): Random =
        mockk<Random>().apply {
            every { nextFloat() } returns value
        }
}
