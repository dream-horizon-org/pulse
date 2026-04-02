package com.pulse.android.sdk.replay

/**
 * Minimal params the SDK passes so the session-replay module can build emitter and integration internally.
 */
public data class SessionReplayBootstrap(
    public val config: SessionReplayConfig,
    public val projectId: String,
    /** Provides current user id for envelope; empty/null is treated as anonymous. */
    public val userIdProvider: () -> String,
    /**
     * Whether session replay should start capturing immediately after install.
     * Pass false when the SDK is initialized with [PulseDataCollectionConsent.PENDING];
     * capture will begin once consent transitions to [PulseDataCollectionConsent.ALLOWED].
     */
    public val isStartActive: Boolean = true,
    /**
     * Provides the current screen name for the replay Meta event.
     * Should return the same value as [screen.name] on OTel log records — i.e. the currently
     * visible Fragment class name, falling back to Activity class name.
     * Defaults to "unknown" if not provided.
     */
    public val screenNameProvider: () -> String = { "unknown" },
)
