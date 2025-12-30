/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.location

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * A [SpanProcessor] implementation that appends location attributes
 * to every span that is exported.
 */
internal class LocationAttributesSpanAppender(
    private val locationProvider: LocationProvider,
) : SpanProcessor {
    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        val locationAttributes = locationProvider.getLocationAttributes()
        span.setAllAttributes(locationAttributes)
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired(): Boolean = false

    companion object {
        @JvmStatic
        internal fun create(locationProvider: LocationProvider): SpanProcessor = LocationAttributesSpanAppender(locationProvider)
    }
}
