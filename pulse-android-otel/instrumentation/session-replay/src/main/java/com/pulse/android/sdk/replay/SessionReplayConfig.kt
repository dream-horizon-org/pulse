package com.pulse.android.sdk.replay

import android.graphics.drawable.Drawable

/**
 * Configuration for Session Replay (matches PostHog SessionReplayConfig).
 *
 * @param maskAllTextInputs If true, mask all text and text input fields (default true).
 * @param maskAllImages If true, mask all images (default true).
 * @param captureLogcat If true, capture logcat as console events (default false; set true and add LogcatIntegration to enable).
 * @param screenshot If true, capture screenshots (PixelCopy); if false, capture view wireframes only.
 * @param throttleDelayMs Minimum delay between snapshots per window (default 1000 ms).
 * @param drawableConverter Optional: convert custom Drawables to Bitmap for wireframe mode.
 * @param screenshotScale Scale factor for screenshot dimensions (0.0, 1.0]. e.g. 0.5 = half width/height (¼ pixels). Default 1.0 = full size.
 * @param screenshotQuality WebP lossy quality 0–100 for screenshot encoding. Lower = smaller size, lower fidelity. Default 30.
 * @param flushIntervalSeconds Interval in seconds to flush the replay queue (default 60).
 * @param flushAt Flush when queue size reaches this many batches (default 10).
 * @param maxBatchSize Maximum number of batches to send per flush / per cached send (default 50).
 */
public class SessionReplayConfig
@JvmOverloads
constructor(
    public var maskAllTextInputs: Boolean = true,
    public var maskAllImages: Boolean = true,
    public var captureLogcat: Boolean = false,
    public var screenshot: Boolean = true,
    public var throttleDelayMs: Long = 1000L,
    public var drawableConverter: DrawableConverter? = null,
    public var screenshotScale: Float = 1f,
    public var screenshotQuality: Int = 30,
    public var flushIntervalSeconds: Int = 60,
    public var flushAt: Int = 10,
    public var maxBatchSize: Int = 50,
)

/**
 * Extension point: convert custom [Drawable] to [android.graphics.Bitmap] for wireframe capture.
 */
public fun interface DrawableConverter {
    public fun convert(drawable: Drawable): android.graphics.Bitmap?
}
