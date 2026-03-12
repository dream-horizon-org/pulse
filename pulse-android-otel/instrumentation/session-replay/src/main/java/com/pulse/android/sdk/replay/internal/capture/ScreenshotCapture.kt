package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.Window
import android.view.PixelCopy
import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import android.os.Handler
import android.os.HandlerThread

import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.events.WireframeType
import com.pulse.android.sdk.replay.internal.util.webpBase64
import com.pulse.android.sdk.replay.internal.util.isValid
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captures a screenshot of [view] (typically decor view) via PixelCopy, applies mask rects,
 * and returns a [ReplayWireframe] with type "screenshot" and base64 image, or null on failure.
 */
internal object ScreenshotCapture {

    private val maskPaint = Paint().apply { color = android.graphics.Color.BLACK }

    private val pixelCopyThread: HandlerThread by lazy {
        HandlerThread("PulseReplayScreenshot").apply { start() }
    }
    private val pixelCopyHandler: Handler by lazy { Handler(pixelCopyThread.looper) }

    /**
     * Capture screenshot. Runs on background. [getMaskRects] is invoked with the view and a list
     * to fill with mask rects (in window coordinates matching the bitmap); returns false if
     * capture should be discarded. Mask rects are collected **before** PixelCopy to avoid
     * timing races with screen changes.
     * @param screenshotScale Scale factor (0.01, 1.0]. e.g. 0.5 = half dimensions. Reduces payload size.
     * @param screenshotQuality WebP lossy quality 0–100. Lower = smaller size.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun capture(
        window: Window,
        view: View,
        displayMetrics: android.util.DisplayMetrics,
        getMaskRects: (View, MutableList<android.graphics.Rect>) -> Boolean,
        onDrawFlag: () -> Boolean,
        setOnDrawFlag: (Boolean) -> Unit,
        logger: (String) -> Unit,
        screenshotScale: Float = 1f,
        screenshotQuality: Int = 30,
    ): ReplayWireframe? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if (!isVisible(view, logger)) return null

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

        // Reset draw flag, then collect mask rects BEFORE PixelCopy to avoid
        // timing race: the view hierarchy must be read while it still matches
        // the screen state that PixelCopy will capture. Collecting inside the
        // async callback risks reading a stale/changed hierarchy.
        setOnDrawFlag(false)
        val maskableWidgets = mutableListOf<android.graphics.Rect>()
        val masksValid = getMaskRects(view, maskableWidgets)

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
                    if (!onDrawFlag() && masksValid) {
                        if (!bitmap.isValid()) {
                            bitmap.recycle()
                            logger("Session Replay Bitmap is invalid")
                            success = false
                            return@request
                        }
                        val canvas = try {
                            Canvas(bitmap)
                        } catch (e: Throwable) {
                            bitmap.recycle()
                            logger("Session Replay Canvas creation failed: $e")
                            success = false
                            return@request
                        }
                        for (rect in maskableWidgets) {
                            if (onDrawFlag()) {
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
                    setOnDrawFlag(false)
                    latch.countDown()
                }
            }, pixelCopyHandler)
        } catch (e: Throwable) {
            bitmap.recycle()
            success = false
            latch.countDown()
        }

        var bitmapRecycled = false
        try {
            latch.await(1000, TimeUnit.MILLISECONDS)
            val scale = screenshotScale.coerceIn(0.01f, 1f)
            val quality = screenshotQuality.coerceIn(0, 100)
            val toEncode = if (scale < 1f && bitmap.isValid()) {
                val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, w, h, true).also {
                    bitmap.recycle()
                    bitmapRecycled = true
                }
            } else {
                bitmap
            }
            val base64 = if (success) toEncode.webpBase64(quality) else null
            toEncode.recycle()
            if (toEncode === bitmap) bitmapRecycled = true
            setOnDrawFlag(false)
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

    internal fun isVisible(view: View, logger: (String) -> Unit): Boolean = view.isVisibleInternal(logger)
    internal fun isViewStateStable(view: View, logger: (String) -> Unit): Boolean = view.isViewStateStableInternal(logger)
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
