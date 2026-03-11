package com.pulse.android.sdk.replay.internal.scheduling

import com.pulse.android.sdk.replay.events.ReplayWireframe

/**
 * Per-decor-view state for snapshot lifecycle.
 * Reset when replay starts with resumeCurrent = false.
 */
internal class ViewTreeSnapshotStatus(
    val listener: NextDrawListener,
    var sentFullSnapshot: Boolean = false,
    var sentMetaEvent: Boolean = false,
    var keyboardVisible: Boolean = false,
    var lastSnapshot: ReplayWireframe? = null,
)
