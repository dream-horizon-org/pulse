package com.pulse.android.sdk.replay

/**
 * Controls replay session start/stop and active state.
 * Implementations clear or preserve per-window snapshot state on start.
 */
public interface SessionReplayController {
    /** Start replay. If [resumeCurrent] is false, next capture will be a full snapshot. */
    public fun start(resumeCurrent: Boolean = false)

    /** Stop replay; no new snapshots will be taken. */
    public fun stop()

    /** True when replay is active and snapshots should be captured. */
    public fun isActive(): Boolean

    /**
     * Overrides Activity/Fragment-based screen names for non-native navigators (React Native, Flutter).
     * When set on [SessionReplayIntegration], takes precedence for replay metadata and screen-change detection.
     */
    public var externalScreenNameProvider: (() -> String?)?

    /**
     * Called when the active screen changes without Activity/Fragment transitions (e.g. JS navigator).
     * Implementations reset snapshot state so the next frame emits meta + full snapshot.
     */
    public fun notifyScreenChange()
}
