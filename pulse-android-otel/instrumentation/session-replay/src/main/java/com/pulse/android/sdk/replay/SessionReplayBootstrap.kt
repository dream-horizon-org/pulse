package com.pulse.android.sdk.replay

import java.util.Objects

/**
 * Minimal params the SDK passes so the session-replay module can build emitter and integration internally.
 */
public class SessionReplayBootstrap(
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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionReplayBootstrap) return false
        return config == other.config &&
            projectId == other.projectId &&
            userIdProvider == other.userIdProvider &&
            isStartActive == other.isStartActive
    }

    override fun hashCode(): Int = Objects.hash(config, projectId, userIdProvider, isStartActive)
}
