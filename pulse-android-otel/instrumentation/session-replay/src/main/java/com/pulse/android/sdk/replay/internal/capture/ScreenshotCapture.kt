package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.events.WireframeType
import com.pulse.android.sdk.replay.internal.util.isValid
import com.pulse.android.sdk.replay.internal.util.webpBase64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captures a screenshot of [view] (typically decor view) via PixelCopy, applies mask rects,
 * and returns a [ReplayWireframe] with type "screenshot" and base64 image, or null on failure.
 */
internal object ScreenshotCapture {
    private val maskPaint = Paint().apply { color = android.graphics.Color.BLACK }

    @Volatile
    private var shutDown = false

    private val pixelCopyThread: HandlerThread by lazy {
        HandlerThread("PulseReplayScreenshot").apply { start() }
    }
    private val pixelCopyHandler: Handler by lazy { Handler(pixelCopyThread.looper) }

    /**
     * Stops the screenshot capture thread. Idempotent. Call from session replay uninstall
     * to avoid thread leak. After this, [capture] and [captureAsync] no-op (return null / invoke callback with null).
     */
    internal fun shutdown() {
        if (shutDown) return
        shutDown = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            pixelCopyThread.quitSafely()
        } else {
            @Suppress("DEPRECATION")
            pixelCopyThread.quit()
        }
        try {
            pixelCopyThread.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Capture screenshot with pre-collected mask rects.
     *
     * Mask rects must be collected on the **main thread** (via [MaskRectCache]) before calling
     * this method so that view coordinates are read atomically in the same frame. This method
     * only performs PixelCopy + mask drawing + encoding on the background thread.
     *
     * @param maskRects Pre-collected mask rects in window coordinates (from main thread).
     * @param masksValid false if the mask collection was aborted (e.g. screen changed mid-walk).
     * @param drawCountAtCollection monotonic counter value captured when masks were collected.
     * @param currentDrawCount returns the current counter; if it differs from [drawCountAtCollection],
     *        the screen changed between mask collection and capture — masks are stale.
     * @param screenshotScale Scale factor (0.01, 1.0]. e.g. 0.5 = half dimensions. Reduces payload size.
     * @param screenshotQuality WebP lossy quality 0–100. Lower = smaller size.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun capture(
        window: Window,
        view: View,
        displayMetrics: android.util.DisplayMetrics,
        maskRects: List<android.graphics.Rect>,
        masksValid: Boolean,
        drawCountAtCollection: Long,
        currentDrawCount: () -> Long,
        logger: (String) -> Unit,
        screenshotScale: Float = 1f,
        screenshotQuality: Int = 30,
    ): ReplayWireframe? {
        if (shutDown) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if (!isVisible(view, logger)) return null
        if (view.width <= 0 || view.height <= 0) return null

        fun screenChangedSinceCollection(): Boolean = currentDrawCount() != drawCountAtCollection

        val viewId = System.identityHashCode(view)
        val coordinates = IntArray(2)
        if (isViewStateStable(view, logger)) {
            view.getLocationOnScreen(coordinates)
        } else {
            coordinates[0] = 0
            coordinates[1] = 0
        }
        val x = coordinates[0].densityValue(displayMetrics.density)
        val y = coordinates[1].densityValue(displayMetrics.density)
        val width = view.width.densityValue(displayMetrics.density)
        val height = view.height.densityValue(displayMetrics.density)

        val maskableWidgets = maskRects

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var success = true

        try {
            PixelCopy.request(window, bitmap, { copyResult ->
                try {
                    if (copyResult != PixelCopy.SUCCESS) {
                        bitmap.recycle()
                        logger("Session Replay PixelCopy failed: $copyResult")
                        success = false
                        return@request
                    }
                    if (!screenChangedSinceCollection() && masksValid) {
                        if (!bitmap.isValid()) {
                            bitmap.recycle()
                            logger("Session Replay Bitmap is invalid")
                            success = false
                            return@request
                        }
                        val canvas =
                            try {
                                Canvas(bitmap)
                            } catch (e: Throwable) {
                                bitmap.recycle()
                                logger("Session Replay Canvas creation failed: $e")
                                success = false
                                return@request
                            }
                        for (rect in maskableWidgets) {
                            if (screenChangedSinceCollection()) {
                                bitmap.recycle()
                                success = false
                                return@request
                            }
                            canvas.drawRoundRect(RectF(rect), 10f, 10f, maskPaint)
                        }
                    } else {
                        bitmap.recycle()
                        success = false
                    }
                } catch (e: Throwable) {
                    bitmap.recycle()
                    logger("Session Replay PixelCopy callback failed: $e")
                } finally {
                    latch.countDown()
                }
            }, pixelCopyHandler)
        } catch (e: Throwable) {
            bitmap.recycle()
            success = false
            latch.countDown()
        }

        @Suppress("KotlinConstantConditions")
        var bitmapRecycled = false
        try {
            latch.await(1000, TimeUnit.MILLISECONDS)
            val scale = screenshotScale.coerceIn(0.01f, 1f)
            val quality = screenshotQuality.coerceIn(0, 100)
            val toEncode =
                if (scale < 1f && bitmap.isValid()) {
                    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                    if (scaled !== bitmap) {
                        bitmap.recycle()
                        bitmapRecycled = true
                    }
                    scaled
                } else {
                    bitmap
                }
            val base64 = if (success) toEncode.webpBase64(quality) else null
            if (toEncode.isValid()) toEncode.recycle()
            bitmapRecycled = true
            return ReplayWireframe(
                id = viewId,
                x = x,
                y = y,
                width = width,
                height = height,
                type = WireframeType.SCREENSHOT,
                base64 = base64,
                style = ReplayStyle(),
            )
        } catch (e: Throwable) {
            if (!bitmapRecycled && bitmap.isValid()) bitmap.recycle()
            logger("Session Replay screenshot await failed: $e")
            return null
        }
    }

    /**
     * Async variant of [capture]: runs PixelCopy and encoding on the capture thread and invokes
     * [onDone] with the result. Does not block the caller (replay worker thread). Use this from
     * the integration to avoid blocking the replay queue for up to 1s per capture.
     */
    @Suppress("LongMethod", "ComplexMethod")
    @RequiresApi(Build.VERSION_CODES.O)
    fun captureAsync(
        window: Window,
        view: View,
        displayMetrics: android.util.DisplayMetrics,
        maskRects: List<android.graphics.Rect>,
        masksValid: Boolean,
        drawCountAtCollection: Long,
        currentDrawCount: () -> Long,
        logger: (String) -> Unit,
        screenshotScale: Float = 1f,
        screenshotQuality: Int = 30,
        onDone: (ReplayWireframe?) -> Unit,
    ) {
        if (shutDown) {
            onDone(null)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onDone(null)
            return
        }
        if (!isVisible(view, logger) || view.width <= 0 || view.height <= 0) {
            onDone(null)
            return
        }

        fun screenChangedSinceCollection(): Boolean = currentDrawCount() != drawCountAtCollection

        val viewId = System.identityHashCode(view)
        val coordinates = IntArray(2)
        if (isViewStateStable(view, logger)) {
            view.getLocationOnScreen(coordinates)
        } else {
            coordinates[0] = 0
            coordinates[1] = 0
        }
        val x = coordinates[0].densityValue(displayMetrics.density)
        val y = coordinates[1].densityValue(displayMetrics.density)
        val width = view.width.densityValue(displayMetrics.density)
        val height = view.height.densityValue(displayMetrics.density)
        val maskableWidgets = maskRects
        val scale = screenshotScale.coerceIn(0.01f, 1f)
        val quality = screenshotQuality.coerceIn(0, 100)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(window, bitmap, { copyResult ->
                try {
                    if (shutDown) {
                        bitmap.recycle()
                        onDone(null)
                        return@request
                    }
                    var success = true
                    if (copyResult != PixelCopy.SUCCESS) {
                        bitmap.recycle()
                        logger("Session Replay PixelCopy failed: $copyResult")
                        success = false
                        onDone(null)
                        return@request
                    }
                    if (!screenChangedSinceCollection() && masksValid) {
                        if (!bitmap.isValid()) {
                            bitmap.recycle()
                            logger("Session Replay Bitmap is invalid")
                            success = false
                            onDone(null)
                            return@request
                        }
                        val canvas =
                            try {
                                Canvas(bitmap)
                            } catch (e: Throwable) {
                                bitmap.recycle()
                                logger("Session Replay Canvas creation failed: $e")
                                onDone(null)
                                return@request
                            }
                        for (rect in maskableWidgets) {
                            if (screenChangedSinceCollection()) {
                                bitmap.recycle()
                                success = false
                                onDone(null)
                                return@request
                            }
                            canvas.drawRoundRect(RectF(rect), 10f, 10f, maskPaint)
                        }
                        val toEncode =
                            if (scale < 1f && bitmap.isValid()) {
                                val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                                val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                                if (scaled !== bitmap) bitmap.recycle()
                                scaled
                            } else {
                                bitmap
                            }
                        val base64 = if (success) toEncode.webpBase64(quality) else null
                        if (toEncode.isValid()) toEncode.recycle()
                        val wireframe = ReplayWireframe(
                            id = viewId,
                            x = x,
                            y = y,
                            width = width,
                            height = height,
                            type = WireframeType.SCREENSHOT,
                            base64 = base64,
                            style = ReplayStyle(),
                        )
                        onDone(wireframe)
                    } else {
                        bitmap.recycle()
                        onDone(null)
                    }
                } catch (e: Throwable) {
                    if (bitmap.isValid()) bitmap.recycle()
                    logger("Session Replay PixelCopy callback failed: $e")
                    onDone(null)
                }
            }, pixelCopyHandler)
        } catch (e: Throwable) {
            bitmap.recycle()
            logger("Session Replay captureAsync failed: $e")
            onDone(null)
        }
    }

    internal fun isVisible(
        view: View,
        logger: (String) -> Unit,
    ): Boolean = view.isVisibleInternal(logger)

    internal fun isViewStateStable(
        view: View,
        logger: (String) -> Unit,
    ): Boolean = view.isViewStateStableInternal(logger)
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun View.isVisibleInternal(logger: (String) -> Unit): Boolean {
    return try {
        if (!isAttachedToWindow) return false
        if (windowVisibility != View.VISIBLE) return false
        var current: Any? = this
        while (current is View) {
            val v = current
            val transitionAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) v.transitionAlpha else 1f
            if (v.alpha <= 0 || transitionAlpha <= 0 || v.visibility != View.VISIBLE) return false
            current = v.parent
        }
        val offset = android.graphics.Point()
        getGlobalVisibleRect(android.graphics.Rect(), offset)
        true
    } catch (e: Throwable) {
        logger("Session Replay isVisible failed: $e")
        true
    }
}

private fun View.isViewStateStableInternal(logger: (String) -> Unit): Boolean =
    try {
        isAttachedToWindow &&
            isLaidOut &&
            width > 0 && height > 0 &&
            !isInLayout &&
            !hasTransientState() &&
            (animation?.hasStarted() != true || animation?.hasEnded() == true) &&
            (parent as? android.view.ViewGroup)?.isInLayout != true &&
            rootView?.isAttachedToWindow == true
    } catch (e: Throwable) {
        logger("Session Replay view state check failed: $e")
        false
    }

private fun Int.densityValue(density: Float): Int = (this / density).toInt()
