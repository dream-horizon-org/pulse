/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.location

import com.google.auto.service.AutoService
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.trace.SpanProcessor

@AutoService(AndroidInstrumentation::class)
class LocationInstrumentation : AndroidInstrumentation {
    internal val locationProviderInstance by lazy {
        initializedLocationProvider
            ?: error("LocationInstrumentation.install() must be called first")
    }

    private var initializedLocationProvider: LocationProvider? = null

    override val name: String = INSTRUMENTATION_NAME

    override fun install(ctx: InstallationContext) {
        val sharedPreferences =
            ctx.application.getSharedPreferences(
                SHARED_PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        val locationProvider =
            LocationProvider(
                ctx.application,
                sharedPreferences,
                CACHE_INVALIDATION_TIME_MS,
            )
        initializedLocationProvider = locationProvider
    }

    companion object {
        @JvmStatic
        fun createSpanProcessor(locationProvider: LocationProvider): SpanProcessor = LocationAttributesSpanAppender.create(locationProvider)

        @JvmStatic
        fun createLogProcessor(locationProvider: LocationProvider): LogRecordProcessor =
            LocationAttributesLogRecordAppender(locationProvider)

        const val INSTRUMENTATION_NAME = "location"
        private const val SHARED_PREFS_NAME = "pulse_location_data"
        private const val CACHE_INVALIDATION_TIME_MS = 3600000L // 1 hour
    }
}
