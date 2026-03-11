package com.pulse.android.sdk.replay

import android.graphics.drawable.Drawable


/**
 * Internal configuration for Session Replay. Default values here act as SDK-level fallbacks.
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
 */
public class SessionReplayConfig
@JvmOverloads
constructor(
    public var textAndInputPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
    public var imagePrivacy: ImagePrivacy = ImagePrivacy.MASK_ALL,
    public var captureLogcat: Boolean = false,
    public var throttleDelayMs: Long = 1000L,
    public var drawableConverter: DrawableConverter? = null,
    public var screenshotScale: Float = 1f,
    public var screenshotQuality: Int = 30,
    public var flushIntervalSeconds: Int = 60,
    public var flushAt: Int = 10,
    public var maxBatchSize: Int = 50,
    public var replayApiBaseUrl: String? = null,
) {
    /** Screenshot capture is always enabled (PixelCopy mode). */
    public val screenshot: Boolean = true

    private val _maskViewClasses: MutableSet<String> = mutableSetOf()
    private val _unmaskViewClasses: MutableSet<String> = mutableSetOf()

    /** Fully-qualified class names whose instances (and subclasses) are always masked. */
    public val maskViewClasses: Set<String> get() = _maskViewClasses

    /** Fully-qualified class names whose instances (and subclasses) are never masked by global config. */
    public val unmaskViewClasses: Set<String> get() = _unmaskViewClasses

    /**
     * Register a view class (by fully-qualified name) to always mask in session replay.
     * Subclasses of this class are also masked. Instance-level overrides still take priority.
     */
    public fun addMaskViewClass(className: String): SessionReplayConfig {
        _maskViewClasses.add(className)
        return this
    }

    /**
     * Register a view class (by fully-qualified name) to never mask by global config.
     * Subclasses of this class are also unmasked. Instance-level overrides still take priority.
     */
    public fun addUnmaskViewClass(className: String): SessionReplayConfig {
        _unmaskViewClasses.add(className)
        return this
    }
}

/**
 * Extension point: convert custom [Drawable] to [android.graphics.Bitmap] for wireframe capture.
 */
public fun interface DrawableConverter {
    public fun convert(drawable: Drawable): android.graphics.Bitmap?
}
