package com.pulse.android.sdk.replay

/**
 * DSL configuration for Session Replay, used inside the instrumentation block
 * (e.g. `instrumentations { sessionReplay { ... } }`).
 *
 * Only code-level integrations that cannot be expressed as backend JSON belong here.
 * All other config (masking policy, quality, URLs, flush params) is controlled by
 * the backend via the `session_replay` feature config.
 */
public class SessionReplayConfiguration {

    /** Optional: convert custom Drawables to Bitmap for wireframe capture mode. */
    public var drawableConverter: DrawableConverter? = null

    private val _maskViewClasses: MutableSet<String> = mutableSetOf()
    private val _unmaskViewClasses: MutableSet<String> = mutableSetOf()

    /** Register a view class (fully-qualified name) to always mask. Subclasses are also masked. */
    public fun addMaskViewClass(className: String) {
        _maskViewClasses.add(className)
    }

    /** Register a view class (fully-qualified name) to never mask by global config. */
    public fun addUnmaskViewClass(className: String) {
        _unmaskViewClasses.add(className)
    }

    internal var configured: Boolean = false
        private set

    /** Called when the user invokes `sessionReplay { }` in the instrumentations block. */
    public fun markConfigured() {
        configured = true
    }

    /** Returns the built [SessionReplayConfig] if [markConfigured] was called; null otherwise. */
    public fun getConfigIfConfigured(): SessionReplayConfig? =
        if (configured) build() else null

    private fun build(): SessionReplayConfig {
        val config = SessionReplayConfig(drawableConverter = drawableConverter)
        _maskViewClasses.forEach { config.addMaskViewClass(it) }
        _unmaskViewClasses.forEach { config.addUnmaskViewClass(it) }
        return config
    }
}
