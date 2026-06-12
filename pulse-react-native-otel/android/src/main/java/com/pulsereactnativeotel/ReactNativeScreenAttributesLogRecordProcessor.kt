package com.pulsereactnativeotel

import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * Log record processor that overrides screen.name with React Native screen name.
 */
internal class ReactNativeScreenAttributesLogRecordProcessor : LogRecordProcessor {
    override fun onEmit(
        context: Context,
        logRecord: ReadWriteLogRecord,
    ) {
        ReactNativeScreenNameTracker.getCurrentScreenName()?.let { screenName ->
            logRecord.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
        }
    }
}

