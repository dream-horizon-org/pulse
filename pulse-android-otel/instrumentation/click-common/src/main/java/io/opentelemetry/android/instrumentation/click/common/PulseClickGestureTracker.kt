/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click.common

import android.view.MotionEvent

/**
 * Tracks ACTION_DOWN / ACTION_UP for tap-vs-scroll discrimination using [android.view.ViewConfiguration]
 * scaled touch slop.
 */
class PulseClickGestureTracker {
    private var lastTouchSlopPx: Int = 0
    private var lastDownX: Float = 0f
    private var lastDownY: Float = 0f
    private var hasValidDown: Boolean = false

    fun setTouchSlopPixels(px: Int) {
        lastTouchSlopPx = px
    }

    fun onActionDown(event: MotionEvent) {
        lastDownX = event.x
        lastDownY = event.y
        hasValidDown = true
    }

    /**
     * @return `true` if this UP should be processed as a tap (movement within touch slop);
     * `false` if there was no valid DOWN or movement exceeded slop (scroll).
     */
    fun onActionUp(event: MotionEvent): Boolean {
        if (!hasValidDown) return false
        hasValidDown = false
        val dx = event.x - lastDownX
        val dy = event.y - lastDownY
        val distanceSq = dx * dx + dy * dy
        return distanceSq <= lastTouchSlopPx * lastTouchSlopPx
    }

    fun onActionCancel() {
        hasValidDown = false
    }
}
