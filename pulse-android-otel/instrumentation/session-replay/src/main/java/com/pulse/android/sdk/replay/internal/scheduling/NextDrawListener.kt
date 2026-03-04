package com.pulse.android.sdk.replay.internal.scheduling

import android.view.View
import android.view.ViewTreeObserver
import com.pulse.android.sdk.replay.internal.util.DateProvider

/**
 * OnDrawListener that fires on the next draw and then uses [Throttler] to limit how often
 * [onDrawThrottlerCallback] runs. [onDrawCallback] runs every draw (e.g. to set a flag that
 * screenshot is stale).
 */
internal class NextDrawListener(
    private val view: View,
    mainHandler: android.os.Handler,
    dateProvider: DateProvider,
    throttleDelayMs: Long,
    private val onDrawCallback: () -> Unit,
    private val onDrawThrottlerCallback: () -> Unit,
) : ViewTreeObserver.OnDrawListener {

    private val throttler = Throttler(mainHandler, dateProvider, throttleDelayMs)

    override fun onDraw() {
        onDrawCallback()
        throttler.throttle(Runnable { onDrawThrottlerCallback() })
    }

    private fun safelyRegisterForNextDraw() {
        if (view.isAlive()) {
            view.viewTreeObserver?.addOnDrawListener(this)
        }
    }

    internal companion object {
        /**
         * Register for the next draw; when it fires, [onDrawThrottlerCallback] is throttled.
         * Call only when decor view is ready.
         */
        fun View.onNextDraw(
            mainHandler: android.os.Handler,
            dateProvider: DateProvider,
            throttleDelayMs: Long,
            onDrawCallback: () -> Unit,
            onDrawThrottlerCallback: () -> Unit,
        ): NextDrawListener {
            val listener = NextDrawListener(
                this,
                mainHandler,
                dateProvider,
                throttleDelayMs,
                onDrawCallback,
                onDrawThrottlerCallback,
            )
            listener.safelyRegisterForNextDraw()
            return listener
        }
    }
}

internal fun View.isAliveAndAttachedToWindow(): Boolean =
    isAlive() && isAttachedToWindow

internal fun View.isAlive(): Boolean =
    viewTreeObserver?.isAlive == true
