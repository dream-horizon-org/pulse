package com.pulse.android.sdk.replay

import android.graphics.drawable.Drawable

/**
 * Immutable configuration for Session Replay. Built once during SDK init and never mutated.
 *
 * **Backend-controlled params** (set by the `session_replay` feature config from the server):
 * @param textAndInputPrivacy Controls masking of text and input fields.
 * @param imagePrivacy Controls masking of images.
 * @param throttleDelayMs Minimum delay between snapshots per window.
 * @param screenshotScale Scale factor for screenshot dimensions (0.0, 1.0].
 * @param screenshotQuality WebP lossy quality 0–100 for screenshot encoding.
 * @param flushIntervalSeconds Interval in seconds to flush the replay queue.
 * @param flushAt Flush when queue size reaches this many batches.
 * @param maxBatchSize Maximum number of batches to send per flush / per cached send.
 * @param replayApiBaseUrl When set, replay batches are sent to this URL via POST to /s/. When null, emitted as OTLP logs only.
 *
 * **Client-only params** (set via the `sessionReplay { }` DSL in app code):
 * @param drawableConverter Optional: convert custom Drawables to Bitmap for wireframe mode.
 * @param captureLogcat If true, capture logcat as console events.
 * @param maskViewClasses Fully-qualified class names whose instances (and subclasses) are always masked.
 * @param unmaskViewClasses Fully-qualified class names whose instances (and subclasses) are never masked by global config.
 */
public data class SessionReplayConfig(
    val textAndInputPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
    val imagePrivacy: ImagePrivacy = ImagePrivacy.MASK_ALL,
    val captureLogcat: Boolean = false,
    val throttleDelayMs: Long = 1000L,
    val drawableConverter: DrawableConverter? = null,
    val screenshotScale: Float = 1f,
    val screenshotQuality: Int = 30,
    val flushIntervalSeconds: Int = 60,
    val flushAt: Int = 10,
    val maxBatchSize: Int = 50,
    val replayApiBaseUrl: String? = null,
    val maskViewClasses: Set<String> = emptySet(),
    val unmaskViewClasses: Set<String> = emptySet(),
) {
    /** Screenshot capture is always enabled (PixelCopy mode). */
    val screenshot: Boolean = true
}

/**
 * Extension point: convert custom [Drawable] to [android.graphics.Bitmap] for wireframe capture.
 */
public fun interface DrawableConverter {
    public fun convert(drawable: Drawable): android.graphics.Bitmap?
}
