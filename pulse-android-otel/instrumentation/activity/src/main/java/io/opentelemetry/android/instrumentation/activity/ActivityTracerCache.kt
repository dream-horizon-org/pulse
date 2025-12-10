/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity

import android.app.Activity
import androidx.annotation.VisibleForTesting
import io.opentelemetry.android.instrumentation.activity.startup.AppStartupTimer
import io.opentelemetry.android.instrumentation.common.ScreenNameExtractor
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenTracker
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.atomic.AtomicReference

/**
 * Encapsulates the fact that we have an ActivityTracer instance per Activity class, and provides
 * convenience methods for adding events and starting spans.
 */
class ActivityTracerCache
    @VisibleForTesting
    internal constructor(
        private val tracerFactory: (Activity) -> ActivityTracer,
    ) {
        private val tracersByActivityClassName: MutableMap<String, ActivityTracer> = mutableMapOf()

        constructor(
            tracer: Tracer,
            visibleScreenTracker: VisibleScreenTracker,
            startupTimer: AppStartupTimer,
            screenNameExtractor: ScreenNameExtractor,
        ) : this(
            tracer,
            visibleScreenTracker,
            AtomicReference<String>(),
            startupTimer,
            screenNameExtractor,
        )

        @VisibleForTesting
        internal constructor(
            tracer: Tracer,
            visibleScreenTracker: VisibleScreenTracker,
            initialAppActivity: AtomicReference<String>,
            startupTimer: AppStartupTimer,
            screenNameExtractor: ScreenNameExtractor,
        ) : this(
            { activity ->
                ActivityTracer
                    .builder(activity)
                    .setScreenName(screenNameExtractor.extract(activity))
                    .setInitialAppActivity(initialAppActivity)
                    .setTracer(tracer)
                    .setAppStartupTimer(startupTimer)
                    .setVisibleScreenTracker(visibleScreenTracker)
                    .build()
            },
        )

        fun addEvent(
            activity: Activity,
            eventName: String?,
        ): ActivityTracer = getTracer(activity).addEvent(eventName)

        fun startSpanIfNoneInProgress(
            activity: Activity,
            spanName: String,
        ): ActivityTracer = getTracer(activity).startSpanIfNoneInProgress(spanName)

        fun initiateRestartSpanIfNecessary(activity: Activity): ActivityTracer {
            val isMultiActivityApp = tracersByActivityClassName.size > 1
            return getTracer(activity).initiateRestartSpanIfNecessary(isMultiActivityApp)
        }

        fun startActivityCreation(activity: Activity): ActivityTracer = getTracer(activity).startActivityCreation()

        fun startActivitySession(activity: Activity): ActivityTracer = getTracer(activity).startActivitySessionSpan()

        fun stopActivitySession(activity: Activity): ActivityTracer = getTracer(activity).stopActivitySessionSpan()

        private fun getTracer(activity: Activity): ActivityTracer =
            tracersByActivityClassName.getOrPut(activity.javaClass.name) {
                val activityTracer = tracerFactory(activity)
                tracersByActivityClassName[activity.javaClass.name] = activityTracer
                activityTracer
            }
    }
