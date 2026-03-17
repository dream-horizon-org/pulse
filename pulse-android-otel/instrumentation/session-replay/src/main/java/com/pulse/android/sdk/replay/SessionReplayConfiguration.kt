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

    private val maskViewClassesMutable: MutableSet<String> = mutableSetOf()
    private val unmaskViewClassesMutable: MutableSet<String> = mutableSetOf()

    /** Register a view class (fully-qualified name) to always mask. Subclasses are also masked. */
    public fun addMaskViewClass(className: String) {
        maskViewClassesMutable.add(className)
    }

    /** Register a view class (fully-qualified name) to never mask by global config. */
    public fun addUnmaskViewClass(className: String) {
        unmaskViewClassesMutable.add(className)
    }

    internal var configured: Boolean = false
        private set

    /** SDK-internal: called when the user invokes `sessionReplay { }` in the instrumentations block. */
    public fun markConfigured() {
        configured = true
    }

    /** SDK-internal: returns the built [SessionReplayConfig] if [markConfigured] was called; null otherwise. */
    public fun getConfigIfConfigured(): SessionReplayConfig? = if (configured) build() else null

    private fun build(): SessionReplayConfig =
        SessionReplayConfig(
            drawableConverter = drawableConverter,
            maskViewClasses = maskViewClassesMutable.toSet(),
            unmaskViewClasses = unmaskViewClassesMutable.toSet(),
        )
}
