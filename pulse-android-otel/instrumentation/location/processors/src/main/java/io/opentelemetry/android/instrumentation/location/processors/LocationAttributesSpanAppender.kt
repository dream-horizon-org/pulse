package io.opentelemetry.android.instrumentation.location.processors

import android.content.SharedPreferences
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * A [SpanProcessor] implementation that appends location attributes
 * to every span that is exported by reading from SharedPreferences.
 */
public class LocationAttributesSpanAppender internal constructor(
    private val sharedPreferences: SharedPreferences,
) : SpanProcessor {
    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        val locationAttributes = LocationAttributesUtils.getLocationAttributesFromCache(sharedPreferences)
        if (locationAttributes.size() > 0) {
            span.setAllAttributes(locationAttributes)
        }
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired(): Boolean = false

    public companion object {
        @JvmStatic
        public fun create(sharedPreferences: SharedPreferences): SpanProcessor = LocationAttributesSpanAppender(sharedPreferences)
    }
}
