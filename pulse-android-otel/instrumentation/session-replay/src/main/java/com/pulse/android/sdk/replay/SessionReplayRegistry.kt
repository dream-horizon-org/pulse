package com.pulse.android.sdk.replay

import java.util.concurrent.atomic.AtomicReference

/**
 * Registry for session replay bootstrap params. The SDK sets [SessionReplayBootstrap] (config + projectId + userIdProvider)
 * before RUM is built; [SessionReplayInstrumentation] reads it during install() and builds emitter and integration internally.
 */
public object SessionReplayRegistry {
    private val pending = AtomicReference<SessionReplayBootstrap?>(null)
    private val integrationRef = AtomicReference<SessionReplayIntegration?>(null)

    /** Called by the SDK before RUM is built. Only config and params; the module builds emitter and integration. */
    public fun set(bootstrap: SessionReplayBootstrap) {
        pending.set(bootstrap)
    }

    /** Called by [SessionReplayInstrumentation.install]. Returns null if not set. */
    internal fun getAndClearPending(): SessionReplayBootstrap? = pending.getAndSet(null)

    /** Called by [SessionReplayInstrumentation.install] after creating the integration. */
    internal fun setIntegration(integration: SessionReplayIntegration) {
        integrationRef.set(integration)
    }

    /** Called by the SDK after initialize() to get the integration for shutdown (flush + uninstall). */
    public fun getIntegration(): SessionReplayIntegration? = integrationRef.get()

    /** Called by the SDK on shutdown after uninstall. */
    public fun clearIntegration() {
        integrationRef.set(null)
    }
}
