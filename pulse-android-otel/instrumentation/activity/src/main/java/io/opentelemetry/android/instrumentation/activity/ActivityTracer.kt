/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity

import android.app.Activity
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.instrumentation.activity.startup.AppStartupTimer
import io.opentelemetry.android.instrumentation.common.ActiveSpan
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenTracker
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import java.util.concurrent.atomic.AtomicReference

class ActivityTracer private constructor(
    builder: Builder,
) {
    private val initialAppActivity: AtomicReference<String> = builder.initialAppActivity
    private val tracer: Tracer = builder.tracer
    private val activityName: String = builder.activityName
    private val screenName: String = builder.screenName
    private val appStartupTimer: AppStartupTimer = builder.appStartupTimer
    private val activeSpan: ActiveSpan = builder.activeSpan
    private var sessionSpan: Span? = null
    private var sessionScope: Scope? = null

    internal fun startSpanIfNoneInProgress(spanName: String): ActivityTracer =
        apply {
            if (activeSpan.spanInProgress()) {
                return this
            }
            activeSpan.startSpanIfNotStarted { createSpanWithParent(spanName, null) }
        }

    internal fun startActivityCreation(): ActivityTracer =
        apply {
            activeSpan.startSpanIfNotStarted { this.makeCreationSpan() }
        }

    private fun makeCreationSpan(): Span {
        // If the application has never loaded an activity, or this is the initial activity getting
        // re-created,
        // we name this span specially to show that it's the application starting up. Otherwise, use
        // the activity class name as the base of the span name.
        val isColdStart = initialAppActivity.get() == null
        if (isColdStart) {
            return createSpanWithParent(CREATED_SPAN_NAME, appStartupTimer.startupSpan)
        }
        if (activityName == initialAppActivity.get()) {
            return createAppStartSpan(WARM_LAUNCH_TYPE)
        }
        return createSpanWithParent(CREATED_SPAN_NAME, null)
    }

    internal fun startActivitySessionSpan(): ActivityTracer =
        apply {
            val spanBuilder =
                tracer.spanBuilder(ACTIVITY_SESSION_SPAN_NAME).apply {
                    setAttribute<String?>(ACTIVITY_NAME_KEY, activityName)
                    setNoParent()
                }

            val span = spanBuilder.startSpan()
            // do this after the span is started, so we can override the default screen.name set by the
            // RumAttributeAppender.
            span.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
            sessionScope = span.makeCurrent()
            sessionSpan = span
        }

    internal fun stopActivitySessionSpan(): ActivityTracer =
        apply {
            sessionSpan?.end()
            sessionScope?.close()
        }

    internal fun initiateRestartSpanIfNecessary(multiActivityApp: Boolean): ActivityTracer {
        if (activeSpan.spanInProgress()) {
            return this
        }
        activeSpan.startSpanIfNotStarted { makeRestartSpan(multiActivityApp) }
        return this
    }

    private fun makeRestartSpan(multiActivityApp: Boolean): Span {
        // restarting the first activity is a "hot" AppStart
        // Note: in a multi-activity application, navigating back to the first activity can trigger
        // this, so it would not be ideal to call it an AppStart.
        if (!multiActivityApp && activityName == initialAppActivity.get()) {
            return createAppStartSpan(HOT_LAUNCH_TYPE)
        }
        return createSpanWithParent(RESTARTED_SPAN_NAME, null)
    }

    private fun createAppStartSpan(startType: String?): Span {
        val span = createSpanWithParent(RumConstants.APP_START_SPAN_NAME, null)
        span.setAttribute(RumConstants.START_TYPE_KEY, startType)
        return span
    }

    private fun createSpanWithParent(
        spanName: String,
        parentSpan: Span?,
    ): Span {
        val spanBuilder =
            tracer.spanBuilder(spanName).setAttribute<String?>(ACTIVITY_NAME_KEY, activityName)
        if (parentSpan != null) {
            spanBuilder.setParent(parentSpan.storeInContext(Context.current()))
        }
        val span = spanBuilder.startSpan()
        // do this after the span is started, so we can override the default screen.name set by the
        // RumAttributeAppender.
        span.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
        return span
    }

    fun endSpanForActivityResumed() {
        if (initialAppActivity.get() == null) {
            initialAppActivity.set(activityName)
        }
        endActiveSpan()
    }

    fun endActiveSpan() {
        // If we happen to be in app startup, make sure this ends it. It's harmless if we're already
        // out of the startup phase.
        appStartupTimer.end()
        activeSpan.endActiveSpan()
    }

    fun addPreviousScreenAttribute(): ActivityTracer =
        apply {
            activeSpan.addPreviousScreenAttribute(activityName)
        }

    fun addEvent(eventName: String?): ActivityTracer =
        apply {
            activeSpan.addEvent(eventName)
        }

    internal class Builder(
        private val activity: Activity,
    ) {
        var screenName: String = "unknown_screen"
            private set

        var initialAppActivity: AtomicReference<String> = AtomicReference()
            private set

        var tracer: Tracer = INVALID_TRACER
            private set

        var appStartupTimer: AppStartupTimer = INVALID_TIMER
            private set

        var activeSpan: ActiveSpan = INVALID_ACTIVE_SPAN
            private set

        fun setVisibleScreenTracker(visibleScreenTracker: VisibleScreenTracker) =
            apply {
                this.activeSpan = ActiveSpan(visibleScreenTracker::previouslyVisibleScreen)
            }

        fun setInitialAppActivity(activityName: String) =
            apply {
                initialAppActivity.set(activityName)
            }

        fun setInitialAppActivity(initialAppActivity: AtomicReference<String>) =
            apply {
                this.initialAppActivity = initialAppActivity
            }

        fun setTracer(tracer: Tracer) =
            apply {
                this.tracer = tracer
            }

        fun setAppStartupTimer(appStartupTimer: AppStartupTimer) =
            apply {
                this.appStartupTimer = appStartupTimer
            }

        fun setActiveSpan(activeSpan: ActiveSpan) =
            apply {
                this.activeSpan = activeSpan
            }

        val activityName: String
            get() = activity.javaClass.simpleName

        fun setScreenName(screenName: String) =
            apply {
                this.screenName = screenName
            }

        fun build(): ActivityTracer {
            check(activeSpan !== INVALID_ACTIVE_SPAN) { "activeSpan must be configured." }
            check(tracer !== INVALID_TRACER) { "tracer must be configured." }
            check(appStartupTimer !== INVALID_TIMER) { "appStartupTimer must be configured." }
            return ActivityTracer(this)
        }

        private companion object {
            private val INVALID_ACTIVE_SPAN = ActiveSpan { null }
            private val INVALID_TRACER = Tracer { _: String? -> null }
            private val INVALID_TIMER = AppStartupTimer()
        }
    }

    internal companion object {
        @JvmField
        internal val ACTIVITY_NAME_KEY: AttributeKey<String?> = AttributeKey.stringKey("activity.name")

        internal fun builder(activity: Activity): Builder = Builder(activity)

        private const val CREATED_SPAN_NAME = "Created"
        private const val ACTIVITY_SESSION_SPAN_NAME = "ActivitySession"
        private const val RESTARTED_SPAN_NAME = "Restarted"
        private const val HOT_LAUNCH_TYPE = "hot"
        private const val WARM_LAUNCH_TYPE = "warm"
    }
}
