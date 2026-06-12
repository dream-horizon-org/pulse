package com.pulse.android.sdk.internal

import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.models.PulseLogRecordData
import com.pulse.android.api.otel.models.PulseMetricData
import com.pulse.android.api.otel.models.PulseSignalData
import com.pulse.android.api.otel.models.PulseSpanData
import com.pulse.android.api.otel.models.copy
import com.pulse.android.sdk.internal.beforesend.PulseBeforeSendLogExporter
import com.pulse.android.sdk.internal.beforesend.PulseBeforeSendMetricExporter
import com.pulse.android.sdk.internal.beforesend.PulseBeforeSendSpanExporter
import com.pulse.utils.createLogRecordData
import com.pulse.utils.createMetricData
import com.pulse.utils.createSpanData
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PulseBeforeSendTest {
    private val spanExporter = InMemorySpanExporter.create()
    private val logExporter = InMemoryLogRecordExporter.create()
    private val metricExporter = InMemoryMetricExporter.create(AggregationTemporality.CUMULATIVE)

    @BeforeEach
    fun setUp() {
        spanExporter.reset()
        logExporter.reset()
        metricExporter.reset()
    }

    @Nested
    inner class BeforeDataSend {
        @Test
        fun `beforeSend is called for each signal`() {
            val receivedSignals = mutableListOf<PulseSignalData>()
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSend(data: PulseSignalData): PulseSignalData {
                        receivedSignals.add(data)
                        return data
                    }
                }

            val spanWrapper = PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
            val logWrapper = PulseBeforeSendLogExporter(beforeSendData, logExporter)
            val metricWrapper = PulseBeforeSendMetricExporter(beforeSendData, metricExporter)

            spanWrapper.export(listOf(createSpanData("span-1"), createSpanData("span-2")))
            logWrapper.export(listOf(createLogRecordData("log-1")))
            metricWrapper.export(listOf(createMetricData("metric-1")))

            assertThat(receivedSignals).hasSize(4)
            assertThat(receivedSignals[0]).isInstanceOf(PulseSpanData::class.java)
            assertThat(receivedSignals[1]).isInstanceOf(PulseSpanData::class.java)
            assertThat(receivedSignals[2]).isInstanceOf(PulseLogRecordData::class.java)
            assertThat(receivedSignals[3]).isInstanceOf(PulseMetricData::class.java)
        }

        @Test
        fun `beforeSendSpan is called for each span`() {
            val receivedSpans = mutableListOf<PulseSpanData>()
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendSpan(data: PulseSpanData): PulseSpanData {
                        receivedSpans.add(data)
                        return data
                    }
                }

            val wrapper = PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
            wrapper.export(listOf(createSpanData("span-a"), createSpanData("span-b")))

            assertThat(receivedSpans).hasSize(2)
            assertThat(spanExporter.finishedSpanItems).hasSize(2)
            assertThat(spanExporter.finishedSpanItems).extracting<String> { it.name }.containsExactly("span-a", "span-b")
        }

        @Test
        fun `beforeSendLog is called for each log`() {
            val receivedLogs = mutableListOf<PulseLogRecordData>()
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendLog(data: PulseLogRecordData): PulseLogRecordData {
                        receivedLogs.add(data)
                        return data
                    }
                }

            val wrapper = PulseBeforeSendLogExporter(beforeSendData, logExporter)
            wrapper.export(listOf(createLogRecordData("log-a"), createLogRecordData("log-b")))

            assertThat(receivedLogs).hasSize(2)
            assertThat(logExporter.finishedLogRecordItems).hasSize(2)
        }

        @Test
        fun `beforeSendMetric is called for each metric`() {
            val receivedMetrics = mutableListOf<PulseMetricData>()
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendMetric(data: PulseMetricData): PulseMetricData {
                        receivedMetrics.add(data)
                        return data
                    }
                }

            val wrapper = PulseBeforeSendMetricExporter(beforeSendData, metricExporter)
            wrapper.export(listOf(createMetricData("metric-a"), createMetricData("metric-b")))

            assertThat(receivedMetrics).hasSize(2)
            assertThat(metricExporter.finishedMetricItems).hasSize(2)
            assertThat(metricExporter.finishedMetricItems).extracting<String> { it.name }.containsExactly("metric-a", "metric-b")
        }

        @Test
        fun `when beforeSend returns null then no data is sent`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSend(data: PulseSignalData): PulseSignalData? = null

                    override fun beforeSendSpan(data: PulseSpanData): PulseSpanData =
                        throw AssertionError("beforeSendSpan should not be called when beforeSend returns null")

                    override fun beforeSendLog(data: PulseLogRecordData): PulseLogRecordData =
                        throw AssertionError("beforeSendLog should not be called when beforeSend returns null")

                    override fun beforeSendMetric(data: PulseMetricData): PulseMetricData =
                        throw AssertionError("beforeSendMetric should not be called when beforeSend returns null")
                }

            PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
                .export(listOf(createSpanData("span-1")))
            PulseBeforeSendLogExporter(beforeSendData, logExporter)
                .export(listOf(createLogRecordData("log-1")))
            PulseBeforeSendMetricExporter(beforeSendData, metricExporter)
                .export(listOf(createMetricData("metric-1")))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
            assertThat(logExporter.finishedLogRecordItems).isEmpty()
            assertThat(metricExporter.finishedMetricItems).isEmpty()
        }

        @Test
        fun `when beforeSendSpan returns null then no span is sent`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendSpan(data: PulseSpanData): PulseSpanData? = null
                }

            PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
                .export(listOf(createSpanData("span-1")))

            assertThat(spanExporter.finishedSpanItems).isEmpty()
        }

        @Test
        fun `when beforeSendLog returns null then no log is sent`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendLog(data: PulseLogRecordData): PulseLogRecordData? = null
                }

            PulseBeforeSendLogExporter(beforeSendData, logExporter)
                .export(listOf(createLogRecordData("log-1")))

            assertThat(logExporter.finishedLogRecordItems).isEmpty()
        }

        @Test
        fun `when beforeSendMetric returns null then no metric is sent`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendMetric(data: PulseMetricData): PulseMetricData? = null
                }

            PulseBeforeSendMetricExporter(beforeSendData, metricExporter)
                .export(listOf(createMetricData("metric-1")))

            assertThat(metricExporter.finishedMetricItems).isEmpty()
        }
    }

    @Nested
    inner class `BeforeDataSend attribute removal` {
        private val userIdKey = AttributeKey.stringKey("user.id")

        private fun attributesWithUserId(): Map<String, Any?> = mapOf("user.id" to "user_id")

        private fun removeUserIdAttribute(attrs: Attributes): Attributes {
            val builder = Attributes.builder()
            attrs.forEach { key, value ->
                if (key != userIdKey) {
                    @Suppress("UNCHECKED_CAST", "CastNullableToNonNullableType")
                    builder.put(key as AttributeKey<Any>, value)
                }
            }
            return builder.build()
        }

        @Test
        fun `beforeSendSpan removes user id attribute from span`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendSpan(data: PulseSpanData): PulseSpanData =
                        data.copy(attributes = removeUserIdAttribute(data.attributes))
                }

            PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
                .export(listOf(createSpanData("span-1", attributesWithUserId())))

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            assertThat(spanExporter.finishedSpanItems[0].attributes.get(userIdKey)).isNull()
        }

        @Test
        fun `beforeSendLog removes user id attribute from log`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendLog(data: PulseLogRecordData): PulseLogRecordData =
                        data.copy(attributes = removeUserIdAttribute(data.attributes))
                }

            PulseBeforeSendLogExporter(beforeSendData, logExporter)
                .export(listOf(createLogRecordData("log-1", attributesWithUserId())))

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter.finishedLogRecordItems[0].attributes.get(userIdKey)).isNull()
        }

        @Test
        fun `beforeSend removes user id attribute from span via generic callback`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSend(data: PulseSignalData): PulseSignalData {
                        if (data is PulseSpanData) {
                            return data.copy(attributes = removeUserIdAttribute(data.attributes))
                        }
                        return data
                    }
                }

            PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
                .export(listOf(createSpanData("span-1", attributesWithUserId())))

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            assertThat(spanExporter.finishedSpanItems[0].attributes.get(userIdKey)).isNull()
        }

        @Test
        fun `beforeSend removes user id attribute from log via generic callback`() {
            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSend(data: PulseSignalData): PulseSignalData {
                        if (data is PulseLogRecordData) {
                            return data.copy(attributes = removeUserIdAttribute(data.attributes))
                        }
                        return data
                    }
                }

            PulseBeforeSendLogExporter(beforeSendData, logExporter)
                .export(listOf(createLogRecordData("log-1", attributesWithUserId())))

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter.finishedLogRecordItems[0].attributes.get(userIdKey)).isNull()
        }

        @Test
        fun `span retains other attributes after user id removal`() {
            val otherKey = AttributeKey.stringKey("other.key")
            val attrs = mapOf("user.id" to "user_id", "other.key" to "keep-me")

            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendSpan(data: PulseSpanData): PulseSpanData =
                        data.copy(attributes = removeUserIdAttribute(data.attributes))
                }

            PulseBeforeSendSpanExporter(beforeSendData, spanExporter)
                .export(listOf(createSpanData("span-1", attrs)))

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            val exported = spanExporter.finishedSpanItems[0]
            assertThat(exported.attributes.get(userIdKey)).isNull()
            assertThat(exported.attributes.get(otherKey)).isEqualTo("keep-me")
        }

        @Test
        fun `log retains other attributes after user id removal`() {
            val otherKey = AttributeKey.stringKey("other.key")
            val attrs = mapOf("user.id" to "user_id", "other.key" to "keep-me")

            val beforeSendData =
                object : PulseBeforeSendData() {
                    override fun beforeSendLog(data: PulseLogRecordData): PulseLogRecordData =
                        data.copy(attributes = removeUserIdAttribute(data.attributes))
                }

            PulseBeforeSendLogExporter(beforeSendData, logExporter)
                .export(listOf(createLogRecordData("log-1", attrs)))

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            val exported = logExporter.finishedLogRecordItems[0]
            assertThat(exported.attributes.get(userIdKey)).isNull()
            assertThat(exported.attributes.get(otherKey)).isEqualTo("keep-me")
        }
    }
}
