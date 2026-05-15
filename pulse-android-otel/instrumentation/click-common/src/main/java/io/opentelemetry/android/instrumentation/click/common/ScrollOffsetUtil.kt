/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click.common

import android.view.View
import android.view.ViewGroup

private const val RECYCLER_VIEW_CLASS_NAME = "androidx.recyclerview.widget.RecyclerView"

/**
 * Walks up the view hierarchy from [view] accumulating scroll offsets.
 * RecyclerView is handled via reflection because its scrollX/scrollY are always 0 —
 * scroll state lives in the LayoutManager.
 */
fun findScrollOffset(view: View): Pair<Int, Int> {
    var scrollXPx = 0
    var scrollYPx = 0
    var current: View? = view
    while (current != null) {
        if (current is ViewGroup) {
            if (RECYCLER_VIEW_CLASS_NAME == current.javaClass.name) {
                scrollXPx += computeScrollOffsetReflective(current, horizontal = true)
                scrollYPx += computeScrollOffsetReflective(current, horizontal = false)
            } else {
                scrollXPx += current.scrollX
                scrollYPx += current.scrollY
            }
        }
        current = current.parent as? View
    }
    return scrollXPx to scrollYPx
}

private fun computeScrollOffsetReflective(
    view: View,
    horizontal: Boolean,
): Int =
    try {
        val method = view.javaClass.getMethod(if (horizontal) "computeHorizontalScrollOffset" else "computeVerticalScrollOffset")
        method.invoke(view) as? Int ?: 0
    } catch (_: Throwable) {
        0
    }
