@file:Suppress("DEPRECATION", "ClassName")

package com.pulse.android.sdk

import com.pulse.otel.utils.PulseOtelUtils
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseSessionAttributes
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PulseSignalProcessorTest {
    private val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val logExporter: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private lateinit var processor: PulseSdkSignalProcessors
    private lateinit var logAppender: PulseSdkSignalProcessors.PulseLogTypeAttributesAppender
    private lateinit var spanAppender: PulseSdkSignalProcessors.PulseSpanTypeAttributesAppender
    private lateinit var tracer: Tracer
    private lateinit var logger: Logger

    @BeforeEach
    fun setUp() {
        spanExporter.reset()
        logExporter.reset()

        processor = PulseSdkSignalProcessors()
        logAppender = processor.PulseLogTypeAttributesAppender()
        spanAppender = PulseSdkSignalProcessors.PulseSpanTypeAttributesAppender()

        val sdkTracerProvider =
            SdkTracerProvider
                .builder()
                .addSpanProcessor(spanAppender)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setResource(Resource.empty())
                .build()

        val sdkLoggerProvider =
            SdkLoggerProvider
                .builder()
                .addLogRecordProcessor(logAppender)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                .setResource(Resource.empty())
                .build()

        val sdk =
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(sdkTracerProvider)
                .setLoggerProvider(sdkLoggerProvider)
                .build()

        tracer = sdk.getTracer("test")
        logger = sdk.logsBridge.loggerBuilder("test").build()
    }

    @Nested
    inner class PulseLogTypeAttributesAppender {
        @Test
        fun `in log, does not set pulse type when pulse type already exists`() {
            logger
                .logRecordBuilder()
                .setEventName("device.crash")
                .setAllAttributes(Attributes.builder().put(PulseAttributes.PULSE_TYPE, "existing.type").build())
                .emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, "existing.type")
        }

        @Test
        fun `in log, sets CRASH type for device crash event`() {
            logger.logRecordBuilder().setEventName("device.crash").emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.CRASH)
        }

        @Test
        fun `in log, sets ANR type for device anr event`() {
            logger.logRecordBuilder().setEventName("device.anr").emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.ANR)
        }

        @Test
        fun `in log, sets FROZEN type for app jank event with frozen threshold`() {
            logger
                .logRecordBuilder()
                .setEventName("app.jank")
                .setAllAttributes(
                    Attributes
                        .builder()
                        .put("app.jank.threshold", PulseSdkSignalProcessors.FROZEN_THRESHOLD_MICRO)
                        .build(),
                ).emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.FROZEN)
        }

        @Test
        fun `in log, sets SLOW type for app jank event with slow threshold`() {
            logger
                .logRecordBuilder()
                .setEventName("app.jank")
                .setAllAttributes(
                    Attributes
                        .builder()
                        .put("app.jank.threshold", PulseSdkSignalProcessors.SLOW_THRESHOLD_MICRO)
                        .build(),
                ).emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.SLOW)
        }

        @Test
        fun `in log, does not set type for app jank event with other threshold value`() {
            logger
                .logRecordBuilder()
                .setEventName("app.jank")
                .setAllAttributes(Attributes.builder().put("app.jank.threshold", 0.5).build())
                .emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .doesNotContainKey(PulseAttributes.PULSE_TYPE)
        }

        @Test
        fun `in log, sets TOUCH type for app screen click event`() {
            logger.logRecordBuilder().setEventName("app.screen.click").emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.TOUCH)
        }

        @Test
        fun `in log, sets TOUCH type for app widget click event`() {
            logger.logRecordBuilder().setEventName("app.widget.click").emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.TOUCH)
        }

        @Test
        fun `in log, sets NETWORK_CHANGE type for network change event`() {
            logger.logRecordBuilder().setEventName("network.change").emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.NETWORK_CHANGE)
        }

        @Test
        fun `in log, does not set type for unknown event`() {
            logger.logRecordBuilder().setEventName("unknown.event").emit()

            assertThat(logExporter.finishedLogRecordItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(logExporter.finishedLogRecordItems[0].attributes)
                .doesNotContainKey(PulseAttributes.PULSE_TYPE)
        }

        @Nested
        inner class `session handling` {
            @Test
            fun `in log, sets session start pulse type`() {
                logger.logRecordBuilder().setEventName("session.end").emit()
                assertThat(logExporter.finishedLogRecordItems).hasSize(1)
                val sessionEndLog = logExporter.finishedLogRecordItems[0]
                assertThat(
                    sessionEndLog.attributes.get(PulseAttributes.PULSE_TYPE),
                ).isEqualTo(PulseAttributes.PulseTypeValues.APP_SESSION_END)
            }

            @Test
            fun `in log, sets session end pulse type`() {
                logger.logRecordBuilder().setEventName("session.start").emit()
                assertThat(logExporter.finishedLogRecordItems).hasSize(1)
                val sessionStartLog = logExporter.finishedLogRecordItems[0]
                assertThat(
                    sessionStartLog.attributes.get(PulseAttributes.PULSE_TYPE),
                ).isEqualTo(PulseAttributes.PulseTypeValues.APP_SESSION_START)
            }

            @Test
            fun `in log, sets session end attributes and clears recorded events on session end`() {
                logger.logRecordBuilder().setEventName("device.crash").emit()
                logger.logRecordBuilder().setEventName("device.anr").emit()

                logger.logRecordBuilder().setEventName(RumConstants.Events.EVENT_SESSION_END).emit()

                assertThat(logExporter.finishedLogRecordItems).hasSize(3)
                val sessionEndLog = logExporter.finishedLogRecordItems[2]
                assertThat(sessionEndLog.eventName).isEqualTo(RumConstants.Events.EVENT_SESSION_END)
                assertSessionEndCounts(sessionEndLog, anrCount = 1L, crashCount = 1L, nonFatalCount = 0L)

                logExporter.reset()
                logger.logRecordBuilder().setEventName(PulseSDKImpl.CUSTOM_NON_FATAL_EVENT_NAME).emit()
                logger.logRecordBuilder().setEventName(RumConstants.Events.EVENT_SESSION_END).emit()

                assertThat(logExporter.finishedLogRecordItems).hasSize(2)
                assertSessionEndCounts(logExporter.finishedLogRecordItems.last(), anrCount = 0L, crashCount = 0L, nonFatalCount = 1L)
            }
        }
    }

    @Nested
    inner class PulseSpanTypeAttributesAppender {
        @Test
        fun `in span, does not set pulse type when pulse type already exists`() {
            val span =
                tracer
                    .spanBuilder("test-span")
                    .setAttribute(PulseAttributes.PULSE_TYPE, "existing.type")
                    .startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, "existing.type")
        }

        @Test
        fun `in span, sets APP_START type for AppStart span with cold start type`() {
            val span =
                tracer
                    .spanBuilder(RumConstants.APP_START_SPAN_NAME)
                    .setAttribute(RumConstants.START_TYPE_KEY, "cold")
                    .startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.APP_START)
        }

        @Test
        fun `in span, does not set APP_START type for AppStart span with warm start type`() {
            val span =
                tracer
                    .spanBuilder(RumConstants.APP_START_SPAN_NAME)
                    .setAttribute(RumConstants.START_TYPE_KEY, "warm")
                    .startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .doesNotContainKey(PulseAttributes.PULSE_TYPE)
        }

        @Test
        fun `in span, sets SCREEN_SESSION type for ActivitySession span`() {
            val span = tracer.spanBuilder("ActivitySession").startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.SCREEN_SESSION)
        }

        @Test
        fun `in span, sets SCREEN_SESSION type for FragmentSession span`() {
            val span = tracer.spanBuilder("FragmentSession").startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.SCREEN_SESSION)
        }

        @Test
        fun `in span, sets SCREEN_LOAD type for Created span`() {
            val span = tracer.spanBuilder("Created").startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .containsEntry(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.SCREEN_LOAD)
        }

        @Nested
        inner class NetworkSpanHandling {
            @Test
            fun `in span, sets network type for span with http method attribute`() {
                val span =
                    tracer
                        .spanBuilder("http-request")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry(PulseAttributes.PULSE_TYPE, "network.0")
            }

            @Test
            fun `in span, normalizes URL when http url attribute is present`() {
                val originalUrl = "https://example.com/users/abc123def456/profile"

                @Suppress("DEPRECATION")
                val span =
                    tracer
                        .spanBuilder("http-request")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .setAttribute(HttpIncubatingAttributes.HTTP_URL, originalUrl)
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                val spanData = spanExporter.finishedSpanItems[0]
                val normalizedUrl = PulseOtelUtils.normaliseUrl(originalUrl)
                OpenTelemetryAssertions
                    .assertThat(spanData.attributes)
                    .containsEntry(HttpIncubatingAttributes.HTTP_URL, normalizedUrl)
            }

            @Test
            fun `in span, sets network type with status code from HTTP_STATUS_CODE`() {
                val span =
                    tracer
                        .spanBuilder("http-request")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .setAttribute(HttpIncubatingAttributes.HTTP_STATUS_CODE, 200L)
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry(PulseAttributes.PULSE_TYPE, "network.200")
            }

            @Test
            fun `in span, sets network type with status code from HTTP_RESPONSE_STATUS_CODE when HTTP_STATUS_CODE is absent`() {
                val span =
                    tracer
                        .spanBuilder("http-request")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404L)
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry(PulseAttributes.PULSE_TYPE, "network.404")
            }

            @Test
            fun `in span, prefers HTTP_STATUS_CODE over HTTP_RESPONSE_STATUS_CODE`() {
                val span =
                    tracer
                        .spanBuilder("http-request")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .setAttribute(HttpIncubatingAttributes.HTTP_STATUS_CODE, 200L)
                        .setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404L)
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry(PulseAttributes.PULSE_TYPE, "network.404")
            }

            @Test
            fun `in network span, url is getting normalised when doesn't contain pulse type`() {
                val span =
                    tracer
                        .spanBuilder("network-req")
                        .setAttribute(HttpIncubatingAttributes.HTTP_URL, "https://api.example.com?param=value")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry(HttpIncubatingAttributes.HTTP_URL, "https://api.example.com")
            }

            @Test
            fun `in network span, url is getting normalised when contain pulse type`() {
                val span =
                    tracer
                        .spanBuilder("network-req")
                        .setAttribute(PulseAttributes.PULSE_TYPE, "react-native")
                        .setAttribute(HttpIncubatingAttributes.HTTP_URL, "https://api.example.com?param=value")
                        .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, "GET")
                        .startSpan()
                span.end()

                assertThat(spanExporter.finishedSpanItems).hasSize(1)
                OpenTelemetryAssertions
                    .assertThat(spanExporter.finishedSpanItems[0].attributes)
                    .containsEntry(HttpIncubatingAttributes.HTTP_URL, "https://api.example.com")
            }
        }

        @Test
        fun `in span, does not set type for unknown span name`() {
            val span = tracer.spanBuilder("unknown-span").startSpan()
            span.end()

            assertThat(spanExporter.finishedSpanItems).hasSize(1)
            OpenTelemetryAssertions
                .assertThat(spanExporter.finishedSpanItems[0].attributes)
                .doesNotContainKey(PulseAttributes.PULSE_TYPE)
        }

        @Test
        fun `in span, isStartRequired returns false`() {
            assertThat(spanAppender.isStartRequired()).isFalse
        }

        @Test
        fun `in span, isEndRequired returns false`() {
            assertThat(spanAppender.isEndRequired()).isFalse
        }

        @Test
        fun `in span, isOnEndingRequired returns true`() {
            assertThat(spanAppender.isOnEndingRequired()).isTrue
        }
    }

    private fun assertSessionEndCounts(
        sessionEndLog: LogRecordData,
        anrCount: Long,
        crashCount: Long,
        nonFatalCount: Long,
    ) {
        OpenTelemetryAssertions
            .assertThat(sessionEndLog.attributes)
            .apply {
                if (anrCount > 0) {
                    containsEntry(PulseSessionAttributes.PULSE_SESSION_ANR_COUNT, anrCount)
                } else {
                    doesNotContainKey(PulseSessionAttributes.PULSE_SESSION_ANR_COUNT)
                }
                if (crashCount > 0) {
                    containsEntry(PulseSessionAttributes.PULSE_SESSION_CRASH_COUNT, crashCount)
                } else {
                    doesNotContainKey(PulseSessionAttributes.PULSE_SESSION_CRASH_COUNT)
                }
                if (nonFatalCount > 0) {
                    containsEntry(PulseSessionAttributes.PULSE_SESSION_CRASH_NON_FATAL, nonFatalCount)
                } else {
                    doesNotContainKey(PulseSessionAttributes.PULSE_SESSION_CRASH_NON_FATAL)
                }
            }
    }
}
