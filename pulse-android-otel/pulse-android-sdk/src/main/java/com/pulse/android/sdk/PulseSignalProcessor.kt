package com.pulse.android.sdk

import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseSessionAttributes
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import java.util.concurrent.ConcurrentHashMap

internal class PulseSignalProcessor {
    private var recordedRelevantLogEvents = ConcurrentHashMap<String, Long>()

    internal inner class PulseLogTypeAttributesAppender : LogRecordProcessor {
        @Suppress("CyclomaticComplexMethod")
        override fun onEmit(
            context: Context,
            logRecord: ReadWriteLogRecord,
        ) {
            if (logRecord.attributes.get(PulseAttributes.PULSE_TYPE) == null) {
                val type =
                    when (logRecord.eventName) {
                        "device.crash" -> {
                            PulseAttributes.PulseTypeValues.CRASH
                        }

                        "device.anr" -> {
                            PulseAttributes.PulseTypeValues.ANR
                        }

                        "app.jank" -> {
                            val threshold =
                                logRecord.attributes.get(AttributeKey.doubleKey("app.jank.threshold"))
                            when (threshold) {
                                null -> null
                                FROZEN_THRESHOLD_MICRO -> {
                                    PulseAttributes.PulseTypeValues.FROZEN
                                }

                                SLOW_THRESHOLD_MICRO -> {
                                    PulseAttributes.PulseTypeValues.SLOW
                                }

                                else -> null
                            }
                        }

                        "app.screen.click", "app.widget.click", "event.app.widget.click" -> PulseAttributes.PulseTypeValues.TOUCH
                        "network.change" -> PulseAttributes.PulseTypeValues.NETWORK_CHANGE
                        "session.end" -> {
                            logRecord.setAllAttributes(
                                PulseSessionAttributes.createSessionEndAttributes(
                                    recordedRelevantLogEvents,
                                ),
                            )
                            recordedRelevantLogEvents.clear()
                            null
                        }

                        else -> null
                    }
                type?.let {
                    logRecord.setAttribute(PulseAttributes.PULSE_TYPE, it)
                }
                logRecord.attributes.get(PulseAttributes.PULSE_TYPE)?.let {
                    recordedRelevantLogEvents.compute(
                        it,
                    ) { _, v -> v?.plus(1) ?: 1 }
                }
            }
        }
    }

    internal inner class PulseSpanTypeAttributesAppender : SpanProcessor {
        override fun onStart(
            parentContext: Context,
            span: ReadWriteSpan,
        ) {
            if (span.attributes.get(PulseAttributes.PULSE_TYPE) == null) {
                val type =
                    when {
                        span.name == RumConstants.APP_START_SPAN_NAME && span.attributes.get(
                            RumConstants.START_TYPE_KEY,
                        ) == "cold" -> {
                            PulseAttributes.PulseTypeValues.APP_START
                        }

                        span.name == "ActivitySession" || span.name == "FragmentSession" -> {
                            PulseAttributes.PulseTypeValues.SCREEN_SESSION
                        }

                        span.name == "Created" -> {
                            PulseAttributes.PulseTypeValues.SCREEN_LOAD
                        }

                        else -> {
                            null
                        }
                    }
                type?.let {
                    span.setAttribute(PulseAttributes.PULSE_TYPE, it)
                }
            }
        }

        override fun isStartRequired(): Boolean = true

        override fun onEnd(span: ReadableSpan) {
            // no-op
        }

        override fun isEndRequired(): Boolean = false
    }

    companion object {
        internal const val SLOW_THRESHOLD_MICRO = 16 / 1000.0
        internal const val FROZEN_THRESHOLD_MICRO = 700 / 1000.0
    }
}
