package io.opentelemetry.android.instrumentation.interaction.library

import android.util.Log
import com.pulse.android.core.InteractionManager
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.instrumentation.interaction.library.InteractionAttributesSpanAppender.Companion.createInteractionAttributes
import io.opentelemetry.api.common.ValueType
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes

class InteractionLogListener(
    private val interactionManager: InteractionManager,
) : LogRecordProcessor {
    override fun onEmit(
        context: Context,
        logRecord: ReadWriteLogRecord,
    ) {
        if (logRecord.bodyValue?.type == ValueType.STRING) {
            Log.d(
                InteractionInstrumentation.LOG_TAG,
                "onEmit in log processor = ${logRecord.bodyValue?.asString()}",
            )
            interactionManager.addEvent(
                logRecord.bodyValue?.asString() ?: error("Null not possible"),
                params = logRecord.attributes.asMap().mapKeys { it.key.key },
                eventTimeInNano = logRecord.observedTimestampEpochNanos,
            )
        }
        if (logRecord.eventName in listOfEventToAddInInteraction) {
            // adding interaction names and id to interested events
            createInteractionAttributes(interactionManager.interactionTrackerStatesState.value)?.let {
                logRecord.setAllAttributes(it)
            }
            interactionManager.addMarkerEvent(
                eventName =
                    logRecord.attributes[PulseAttributes.PULSE_TYPE]
                        ?: error("${PulseAttributes.PULSE_TYPE.key} is not defined for eventName = ${logRecord.eventName}"),
                eventTimeInNano = logRecord.observedTimestampEpochNanos,
                params =
                    mapOf(
                        LogIncubatingAttributes.LOG_RECORD_UID.key to logRecord.attributes[LogIncubatingAttributes.LOG_RECORD_UID],
                    ),
            )
        }
    }

    private companion object {
        private val listOfEventToAddInInteraction =
            listOf(
                "device.crash",
                "device.anr",
                "app.jank",
                "network.change",
                "app.screen.click",
            )
    }
}
