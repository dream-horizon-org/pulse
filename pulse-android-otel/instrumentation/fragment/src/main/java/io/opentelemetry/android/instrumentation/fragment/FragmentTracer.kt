/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.fragment

import androidx.fragment.app.Fragment
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.instrumentation.common.ActiveSpan
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope

internal class FragmentTracer private constructor(
    builder: Builder,
) {
    private val fragmentName: String = builder.getFragmentName()
    private val screenName: String? = builder.screenName
    private val tracer: Tracer = builder.tracer
    private val activeSpan: ActiveSpan = builder.activeSpan
    private var sessionSpan: Span? = null
    private var sessionScope: Scope? = null

    fun startSpanIfNoneInProgress(action: String) =
        apply {
            if (activeSpan.spanInProgress()) {
                return this
            }
            activeSpan.startSpanIfNotStarted { createSpan(action) }
        }

    fun startFragmentCreation() =
        apply {
            activeSpan.startSpanIfNotStarted { createSpan("Created") }
        }

    fun startFragmentSessionSpan(): FragmentTracer =
        apply {
            val spanBuilder =
                tracer.spanBuilder("FragmentSession").apply {
                    setAttribute<String?>(FRAGMENT_NAME_KEY, fragmentName)
                }

            val span = spanBuilder.startSpan()
            // do this after the span is started, so we can override the default screen.name set by the
            // RumAttributeAppender.
            span.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
            sessionSpan = span
            sessionScope = span.makeCurrent()
        }

    fun stopFragmentSessionSpan(): FragmentTracer =
        apply {
            sessionSpan?.end()
            sessionScope?.close()
        }

    private fun createSpan(spanName: String): Span {
        val span =
            tracer
                .spanBuilder(spanName)
                .setAttribute<String?>(FRAGMENT_NAME_KEY, fragmentName)
                .startSpan()
        // do this after the span is started, so we can override the default screen.name set by the
        // RumAttributeAppender.
        span.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
        return span
    }

    fun endActiveSpan() {
        activeSpan.endActiveSpan()
    }

    fun addPreviousScreenAttribute(): FragmentTracer {
        activeSpan.addPreviousScreenAttribute(fragmentName)
        return this
    }

    fun addEvent(eventName: String?) =
        apply {
            activeSpan.addEvent(eventName)
        }

    internal class Builder(
        private val fragment: Fragment,
    ) {
        var screenName: String? = ""
        var tracer: Tracer = INVALID_TRACER
            private set
        var activeSpan: ActiveSpan = INVALID_ACTIVE_SPAN
            private set

        fun setTracer(tracer: Tracer) =
            apply {
                this.tracer = tracer
            }

        fun setScreenName(screenName: String?) =
            apply {
                this.screenName = screenName
            }

        fun setActiveSpan(activeSpan: ActiveSpan) =
            apply {
                this.activeSpan = activeSpan
            }

        fun getFragmentName(): String = fragment.javaClass.getSimpleName()

        fun build(): FragmentTracer {
            check(activeSpan !== INVALID_ACTIVE_SPAN) { "activeSpan must be configured." }
            check(tracer !== INVALID_TRACER) { "tracer must be configured." }
            return FragmentTracer(this)
        }

        companion object {
            private val INVALID_ACTIVE_SPAN = ActiveSpan { null }
            private val INVALID_TRACER = Tracer { spanName: String? -> null }
        }
    }

    companion object {
        val FRAGMENT_NAME_KEY: AttributeKey<String?> = AttributeKey.stringKey("fragment.name")

        @JvmStatic
        fun builder(fragment: Fragment): Builder = Builder(fragment)
    }
}
