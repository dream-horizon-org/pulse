package com.pulse.android.sdk.replay.internal.scheduling

import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.internal.capture.MaskRectCache

/**
 * Per-decor-view state for snapshot lifecycle.
 * Each decor view (window) gets its own [maskRectCache] so mask positions
 * are never shared across different screens during navigation or dialogs.
 * Reset when replay starts with resumeCurrent = false.
 */
internal class ViewTreeSnapshotStatus(
    val listener: NextDrawListener,
    val maskRectCache: MaskRectCache,
    var sentFullSnapshot: Boolean = false,
    var sentMetaEvent: Boolean = false,
    var keyboardVisible: Boolean = false,
    var lastSnapshot: ReplayWireframe? = null,
)
