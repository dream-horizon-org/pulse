package com.pulse.android.sdk.replay

import android.graphics.drawable.Drawable

/**
 * Immutable configuration for Session Replay. Built once during SDK init and never mutated.
 *
 * **Backend-controlled params** (set by the `session_replay` feature config from the server):
 * @property textAndInputPrivacy Controls masking of text and input fields.
 * @property imagePrivacy Controls masking of images.
 * @property isCaptureLogcat If true, capture logcat as console events.
 * @property throttleDelayMs Minimum delay between snapshots per window.
 * @property drawableConverter Optional: convert custom Drawables to Bitmap for wireframe mode.
 * @property screenshotScale Scale factor for screenshot dimensions (0.0, 1.0].
 * @property screenshotQuality WebP lossy quality 0–100 for screenshot encoding.
 * @property flushIntervalSeconds Interval in seconds to flush the replay queue.
 * @property flushAt When the pending batch queue reaches this size, a flush is triggered. Also caps how many
 *   replay batch files are uploaded in a single backend request (per flush and per cached-send chunk).
 * @property maxBatchSize Maximum number of replay batch files kept on disk (including the in-memory queue).
 *   When exceeded, the oldest batches are deleted (latest-first retention).
 * @property replayApiBaseUrl When set, replay batches are sent to this URL via POST to /s/. When null, emitted as OTLP logs only.
 *
 * Client-only params (set via the `sessionReplay { }` DSL in app code):
 * @property maskViewClasses Fully-qualified class names whose instances (and subclasses) are always masked.
 * @property unmaskViewClasses Fully-qualified class names whose instances (and subclasses) are never masked by global config.
 */
public class SessionReplayConfig(
    public val textAndInputPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
    public val imagePrivacy: ImagePrivacy = ImagePrivacy.MASK_ALL,
    public val isCaptureLogcat: Boolean = false,
    public val throttleDelayMs: Long = 2000L,
    public val drawableConverter: DrawableConverter? = null,
    public val screenshotScale: Float = 0.3f,
    public val screenshotQuality: Int = 30,
    public val flushIntervalSeconds: Int = 60,
    public val flushAt: Int = 10,
    public val maxBatchSize: Int = 50,
    public val replayApiBaseUrl: String? = null,
    public val maskViewClasses: Set<String> = emptySet(),
    public val unmaskViewClasses: Set<String> = emptySet(),
) {
    /** Screenshot capture is always enabled (PixelCopy mode). */
    public val isScreenshot: Boolean = true

    /** Scale factor clamped to valid range. */
    public val effectiveScreenshotScale: Float get() = screenshotScale.coerceIn(0.01f, 1f)

    /** Quality clamped to valid range. */
    public val effectiveScreenshotQuality: Int get() = screenshotQuality.coerceIn(0, 100)

    /** Throttle delay clamped to minimum 100ms. */
    public val effectiveThrottleDelayMs: Long get() = throttleDelayMs.coerceAtLeast(100L)

    /** Flush interval clamped to minimum 1 second. */
    public val effectiveFlushIntervalSeconds: Int get() = flushIntervalSeconds.coerceAtLeast(1)

    /** Flush-at threshold clamped to minimum 1. */
    public val effectiveFlushAt: Int get() = flushAt.coerceAtLeast(1)

    /** Max batch size clamped to minimum 1. */
    public val effectiveMaxBatchSize: Int get() = maxBatchSize.coerceAtLeast(1)

    public fun copy(
        textAndInputPrivacy: TextAndInputPrivacy = this.textAndInputPrivacy,
        imagePrivacy: ImagePrivacy = this.imagePrivacy,
        isCaptureLogcat: Boolean = this.isCaptureLogcat,
        throttleDelayMs: Long = this.throttleDelayMs,
        drawableConverter: DrawableConverter? = this.drawableConverter,
        screenshotScale: Float = this.screenshotScale,
        screenshotQuality: Int = this.screenshotQuality,
        flushIntervalSeconds: Int = this.flushIntervalSeconds,
        flushAt: Int = this.flushAt,
        maxBatchSize: Int = this.maxBatchSize,
        replayApiBaseUrl: String? = this.replayApiBaseUrl,
        maskViewClasses: Set<String> = this.maskViewClasses,
        unmaskViewClasses: Set<String> = this.unmaskViewClasses,
    ): SessionReplayConfig =
        SessionReplayConfig(
            textAndInputPrivacy = textAndInputPrivacy,
            imagePrivacy = imagePrivacy,
            isCaptureLogcat = isCaptureLogcat,
            throttleDelayMs = throttleDelayMs,
            drawableConverter = drawableConverter,
            screenshotScale = screenshotScale,
            screenshotQuality = screenshotQuality,
            flushIntervalSeconds = flushIntervalSeconds,
            flushAt = flushAt,
            maxBatchSize = maxBatchSize,
            replayApiBaseUrl = replayApiBaseUrl,
            maskViewClasses = maskViewClasses,
            unmaskViewClasses = unmaskViewClasses,
        )
}

/**
 * Extension point: convert custom [Drawable] to [android.graphics.Bitmap] for wireframe capture.
 */
public fun interface DrawableConverter {
    public fun convert(drawable: Drawable): android.graphics.Bitmap?
}
