package com.pulse.sampling.core.exporters

import com.pulse.sampling.core.PulseSignalMatcher
import com.pulse.sampling.core.PulseSignalsAttrMatcher
import com.pulse.sampling.models.PulseSdkConfigFakeUtils.createFakeProp
import com.pulse.sampling.models.PulseSdkConfigFakeUtils.createFakeSignalMatchCondition
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import com.pulse.utils.createLogRecordData
import com.pulse.utils.createSpanData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PulseSignalSelectExporterTest {
    private val spanExporter1: InMemorySpanExporter = InMemorySpanExporter.create()
    private val spanExporter2: InMemorySpanExporter = InMemorySpanExporter.create()
    private val spanExporter3: InMemorySpanExporter = InMemorySpanExporter.create()
    private val logExporter1: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private val logExporter2: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher()

    @BeforeEach
    fun setUp() {
        spanExporter1.reset()
        spanExporter2.reset()
        spanExporter3.reset()
        logExporter1.reset()
        logExporter2.reset()
    }

    @Nested
    inner class SpanExporterTest {
        @Test
        fun `should export span to matching exporter when matcher matches`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = "span-.*",
                    scopes = setOf(PulseSignalScope.TRACES),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val matchCondition2 =
                createFakeSignalMatchCondition(
                    name = "other-.*",
                    scopes = setOf(PulseSignalScope.TRACES),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to spanExporter1,
                    matchCondition2 to spanExporter2,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedSpanExporter(map)

            val span = createSpanData("span-test", emptyMap())

            val result = selectedExporter.export(listOf(span))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(spanExporter1.finishedSpanItems).hasSize(1)
            assertThat(spanExporter1.finishedSpanItems[0].name).isEqualTo("span-test")
            assertThat(spanExporter2.finishedSpanItems).isEmpty()
        }

        @Test
        fun `should export span to last matching exporter when multiple matchers match`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.TRACES),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val matchCondition2 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.TRACES),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to spanExporter1,
                    matchCondition2 to spanExporter2,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedSpanExporter(map)

            val span = createSpanData("test-span", emptyMap())

            val result = selectedExporter.export(listOf(span))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            // Only the last matching exporter should receive the span
            assertThat(spanExporter1.finishedSpanItems).isEmpty()
            assertThat(spanExporter2.finishedSpanItems).hasSize(1)
            assertThat(spanExporter2.finishedSpanItems[0].name).isEqualTo("test-span")
        }

        @Test
        fun `should not export span when no matcher matches`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = "other-.*",
                    scopes = setOf(PulseSignalScope.TRACES),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to spanExporter1,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedSpanExporter(map)

            val span = createSpanData("test-span", emptyMap())

            val result = selectedExporter.export(listOf(span))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(spanExporter1.finishedSpanItems).isEmpty()
        }

        @Test
        fun `should handle empty spans collection`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.TRACES),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to spanExporter1,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedSpanExporter(map)

            val result = selectedExporter.export(emptyList())

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(spanExporter1.finishedSpanItems).isEmpty()
        }

        @Test
        fun `should handle empty map of matchers`() {
            val map =
                emptyList<Pair<PulseSignalMatchCondition, SpanExporter>>()
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedSpanExporter(map)

            val span = createSpanData("test-span", emptyMap())

            val result = selectedExporter.export(listOf(span))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
        }
    }

    @Nested
    inner class LogExporterTest {
        @Test
        fun `should export log to matching exporter when matcher matches`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = "log-.*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val matchCondition2 =
                createFakeSignalMatchCondition(
                    name = "other-.*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                    matchCondition2 to logExporter2,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val log = createLogRecordData("log-test", emptyMap())

            val result = selectedExporter.export(listOf(log))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(logExporter1.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter1.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("log-test")
            assertThat(logExporter2.finishedLogRecordItems).isEmpty()
        }

        @Test
        fun `should export log to last matching exporter when multiple matchers match`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val matchCondition2 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                    matchCondition2 to logExporter2,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val log = createLogRecordData("test-log", emptyMap())

            val result = selectedExporter.export(listOf(log))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            // Only the last matching exporter should receive the log
            assertThat(logExporter1.finishedLogRecordItems).isEmpty()
            assertThat(logExporter2.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter2.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
        }

        @Test
        fun `should not export log when no matcher matches`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = "other-.*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val log = createLogRecordData("test-log", emptyMap())

            val result = selectedExporter.export(listOf(log))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(logExporter1.finishedLogRecordItems).isEmpty()
        }

        @Test
        fun `should handle empty logs collection`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val result = selectedExporter.export(emptyList())

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(logExporter1.finishedLogRecordItems).isEmpty()
        }

        @Test
        fun `should export log with attributes to matching exporter`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = "test-log",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                    props = setOf(createFakeProp("key1", "value1")),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val log = createLogRecordData("test-log", mapOf("key1" to "value1"))

            val result = selectedExporter.export(listOf(log))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
            assertThat(logExporter1.finishedLogRecordItems).hasSize(1)
            assertThat(logExporter1.finishedLogRecordItems[0].bodyValue?.asString()).isEqualTo("test-log")
            assertThat(
                logExporter1.finishedLogRecordItems[0].attributes.get(
                    io.opentelemetry.api.common.AttributeKey
                        .stringKey("key1"),
                ),
            ).isEqualTo(
                "value1",
            )
        }

        @Test
        fun `should handle empty map of matchers for logs`() {
            val map =
                emptyList<Pair<PulseSignalMatchCondition, LogRecordExporter>>()
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val log = createLogRecordData("test-log", emptyMap())

            val result = selectedExporter.export(listOf(log))

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
        }

        @Test
        fun `should flush all log exporters`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val matchCondition2 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                    matchCondition2 to logExporter2,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val result = selectedExporter.flush()

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
        }

        @Test
        fun `should shutdown all log exporters`() {
            val matchCondition1 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val matchCondition2 =
                createFakeSignalMatchCondition(
                    name = ".*",
                    scopes = setOf(PulseSignalScope.LOGS),
                    sdks = setOf(PulseSdkName.ANDROID_JAVA),
                )
            val map =
                listOf(
                    matchCondition1 to logExporter1,
                    matchCondition2 to logExporter2,
                )
            val selectExporter = PulseSignalSelectExporter(PulseSdkName.ANDROID_JAVA, signalMatcher)
            val selectedExporter = selectExporter.SelectedLogExporter(map)

            val result = selectedExporter.shutdown()

            assertThat(result).isNotNull
            assertThat(result.isSuccess).isTrue
        }
    }
}
