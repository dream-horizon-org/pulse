package com.pulse.android.sdk.replay

/**
 * DSL configuration for Session Replay, used inside the instrumentation block (e.g. `instrumentations { sessionReplay { ... } }`).
 * Configure options here; the built [SessionReplayConfig] is used when session replay is enabled.
 */
public class SessionReplayConfiguration {
    public var textAndInputPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL
    public var imagePrivacy: ImagePrivacy = ImagePrivacy.MASK_ALL
    public var captureLogcat: Boolean = false
    public var screenshot: Boolean = true
    public var throttleDelayMs: Long = 1000L
    public var drawableConverter: DrawableConverter? = null
    public var screenshotScale: Float = 1f
    public var screenshotQuality: Int = 30
    public var flushIntervalSeconds: Int = 60
    public var flushAt: Int = 10
    public var maxBatchSize: Int = 50
    public var replayApiBaseUrl: String? = null

    private val _maskViewClasses: MutableSet<String> = mutableSetOf()
    private val _unmaskViewClasses: MutableSet<String> = mutableSetOf()

    public fun addMaskViewClass(className: String) {
        _maskViewClasses.add(className)
    }

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
        val config = SessionReplayConfig(
            textAndInputPrivacy = textAndInputPrivacy,
            imagePrivacy = imagePrivacy,
            captureLogcat = captureLogcat,
            screenshot = screenshot,
            throttleDelayMs = throttleDelayMs,
            drawableConverter = drawableConverter,
            screenshotScale = screenshotScale,
            screenshotQuality = screenshotQuality,
            flushIntervalSeconds = flushIntervalSeconds,
            flushAt = flushAt,
            maxBatchSize = maxBatchSize,
            replayApiBaseUrl = replayApiBaseUrl,
        )
        _maskViewClasses.forEach { config.addMaskViewClass(it) }
        _unmaskViewClasses.forEach { config.addUnmaskViewClass(it) }
        return config
    }
}
