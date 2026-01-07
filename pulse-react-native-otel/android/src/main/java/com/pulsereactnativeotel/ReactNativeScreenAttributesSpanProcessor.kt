package com.pulsereactnativeotel

import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Span processor that overrides screen.name with React Native screen name.
 */
internal class ReactNativeScreenAttributesSpanProcessor : SpanProcessor {
    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        ReactNativeScreenNameTracker.getCurrentScreenName()?.let { screenName ->
            span.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
        }
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {
        // No-op
    }

    override fun isEndRequired(): Boolean = false
}

