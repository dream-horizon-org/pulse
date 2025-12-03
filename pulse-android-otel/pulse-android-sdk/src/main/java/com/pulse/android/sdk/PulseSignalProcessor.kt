package com.pulse.android.sdk

import com.pulse.otel.utils.PulseOtelUtils
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseSessionAttributes
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.internal.ExtendedSpanProcessor
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
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

    internal inner class PulseSpanTypeAttributesAppender : ExtendedSpanProcessor {
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
        
        override fun onEnding(span: ReadWriteSpan) {
            if (PulseOtelUtils.isNetworkSpan(span)) {
                // todo when https://github.com/open-telemetry/opentelemetry-android/issues/1393 is fixed
                //  use the new not deprecated attributes
                @Suppress("DEPRECATION") val httpUrlKey: AttributeKey<String> = HttpIncubatingAttributes.HTTP_URL
                val originalUrl = span.attributes.get(httpUrlKey)
                
                originalUrl?.let {
                    span.setAttribute(httpUrlKey, PulseOtelUtils.normaliseUrl(it))
                }
                android.util.Log.d("[Pulse]", "onEnding: $originalUrl")
                android.util.Log.d("[Pulse]", "onEnding: ${span.attributes.get(httpUrlKey)}")
            }
        }

        override fun isOnEndingRequired(): Boolean = true
    }

    companion object {
        internal const val SLOW_THRESHOLD_MICRO = 16 / 1000.0
        internal const val FROZEN_THRESHOLD_MICRO = 700 / 1000.0
    }
}
