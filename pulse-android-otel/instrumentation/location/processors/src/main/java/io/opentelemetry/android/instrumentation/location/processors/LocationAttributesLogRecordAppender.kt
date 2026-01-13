package io.opentelemetry.android.instrumentation.location.processors

import android.content.SharedPreferences
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A [LogRecordProcessor] implementation that appends location attributes
 * by reading from SharedPreferences.
 */
public class LocationAttributesLogRecordAppender internal constructor(
    private val sharedPreferences: SharedPreferences,
) : LogRecordProcessor {
    override fun onEmit(
        context: Context,
        logRecord: ReadWriteLogRecord,
    ) {
        val locationAttributes = LocationAttributesUtils.getLocationAttributesFromCache(sharedPreferences)
        if (locationAttributes.size() > 0) {
            logRecord.setAllAttributes(locationAttributes)
        }
    }

    public companion object {
        @JvmStatic
        public fun create(sharedPreferences: SharedPreferences): LogRecordProcessor = LocationAttributesLogRecordAppender(sharedPreferences)
    }
}
