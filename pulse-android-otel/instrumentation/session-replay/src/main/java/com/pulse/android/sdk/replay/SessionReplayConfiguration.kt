package com.pulse.android.sdk.replay

import android.graphics.drawable.Drawable

/**
 * DSL configuration for Session Replay, used inside the instrumentation block (e.g. `instrumentations { sessionReplay { ... } }`).
 * Configure options here; the built [SessionReplayConfig] is used when session replay is enabled.
 */
public class SessionReplayConfiguration {
    public var maskAllTextInputs: Boolean = true
    public var maskAllImages: Boolean = true
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

    internal var configured: Boolean = false
        private set

    /** Called when the user invokes `sessionReplay { }` in the instrumentations block. */
    public fun markConfigured() {
        configured = true
    }

    /** Returns the built [SessionReplayConfig] if [markConfigured] was called; null otherwise. */
    public fun getConfigIfConfigured(): SessionReplayConfig? =
        if (configured) build() else null

    private fun build(): SessionReplayConfig = SessionReplayConfig(
        maskAllTextInputs = maskAllTextInputs,
        maskAllImages = maskAllImages,
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
}
