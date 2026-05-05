package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.events.WireframeType
import com.pulse.android.sdk.replay.internal.util.isValid
import com.pulse.android.sdk.replay.internal.util.webpBase64
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * View dimensions and position collected on the main thread before [capture] / [captureAsync]
 * run on a background thread (PixelCopy must not read [View] layout from a worker).
 */
internal data class ScreenshotLayoutSnapshot(
    val viewId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val locationX: Int,
    val locationY: Int,
)

/**
 * Reads layout from [view] on the **main thread** only. Returns null if the view is not
 * suitable for capture.
 */
@UiThread
internal fun collectScreenshotLayout(
    view: View,
    logger: (String) -> Unit,
): ScreenshotLayoutSnapshot? {
    if (!ScreenshotCapture.isVisible(view, logger)) return null
    if (view.width <= 0 || view.height <= 0) return null
    val coordinates = IntArray(2)
    if (ScreenshotCapture.isViewStateStable(view, logger)) {
        view.getLocationOnScreen(coordinates)
    } else {
        coordinates[0] = 0
        coordinates[1] = 0
    }
    return ScreenshotLayoutSnapshot(
        viewId = System.identityHashCode(view),
        widthPx = view.width,
        heightPx = view.height,
        locationX = coordinates[0],
        locationY = coordinates[1],
    )
}

/**
 * Captures a screenshot of [view] (typically decor view) via PixelCopy, applies mask rects,
 * and returns a [ReplayWireframe] with type "screenshot" and base64 image, or null on failure.
 */
internal object ScreenshotCapture {
    private val maskPaint = Paint().apply { color = android.graphics.Color.BLACK }

    @Volatile
    private var isShutDown = false

    private val pixelCopyThread: HandlerThread by lazy {
        HandlerThread("PulseReplayScreenshot").apply { start() }
    }
    private val pixelCopyHandler: Handler by lazy { Handler(pixelCopyThread.looper) }

    /**
     * Stops the screenshot capture thread. Idempotent. Call from session replay uninstall
     * to avoid thread leak. After this, [capture] and [captureAsync] no-op (return null / invoke callback with null).
     */
    internal fun shutdown() {
        if (isShutDown) return
        isShutDown = true
        pixelCopyThread.quitSafely()
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
     * @param window [Window] to capture
     * @param layout [ScreenshotLayoutSnapshot] of the captured view
     * @param displayMetrics TBA by <anirudh.bharti> for session replay
     * @param maskRects Pre-collected mask rects in window coordinates (from main thread).
     * @param masksValid false if the mask collection was aborted (e.g. screen changed mid-walk).
     * @param drawCountAtCollection monotonic counter value captured when masks were collected.
     * @param currentDrawCount returns the current counter; if it differs from [drawCountAtCollection],
     *        the screen changed between mask collection and capture — masks are stale.
     * @param logger Logger to log the data
     * @param screenshotScale Scale factor (0.01, 1.0]. e.g. 0.5 = half dimensions. Reduces payload size.
     * @param screenshotQuality WebP lossy quality 0–100. Lower = smaller size.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @WorkerThread
    fun capture(
        window: Window,
        layout: ScreenshotLayoutSnapshot,
        displayMetrics: android.util.DisplayMetrics,
        maskRects: List<android.graphics.Rect>,
        masksValid: Boolean,
        drawCountAtCollection: Long,
        currentDrawCount: () -> Long,
        logger: (String) -> Unit,
        screenshotScale: Float = 1f,
        screenshotQuality: Int = 30,
    ): ReplayWireframe? {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "capture() must not be called on the main thread"
        }
        if (isShutDown) return null

        val screenChanged = { currentDrawCount() != drawCountAtCollection }

        val viewId = layout.viewId
        val x = layout.locationX.densityValue(displayMetrics.density)
        val y = layout.locationY.densityValue(displayMetrics.density)
        val width = layout.widthPx.densityValue(displayMetrics.density)
        val height = layout.heightPx.densityValue(displayMetrics.density)

        val bitmap = createBitmap(layout.widthPx, layout.heightPx, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var isSuccess = true

        try {
            PixelCopy.request(window, bitmap, { copyResult ->
                try {
                    isSuccess =
                        handleCaptureCopyResult(
                            copyResult = copyResult,
                            bitmap = bitmap,
                            screenChanged = screenChanged,
                            masksValid = masksValid,
                            maskRects = maskRects,
                            logger = logger,
                        )
                } catch (e: Throwable) {
                    bitmap.recycle()
                    logger("Session Replay PixelCopy callback failed: $e")
                } finally {
                    latch.countDown()
                }
            }, pixelCopyHandler)
        } catch (e: Throwable) {
            bitmap.recycle()
            logger("Session Replay PixelCopy request failed: $e")
            isSuccess = false
        } finally {
            // If PixelCopy.request() itself threw, the callback was never posted.
            // Count down so latch.await() unblocks. If the callback already ran,
            // CountDownLatch ignores redundant countdowns (count is already 0).
            latch.countDown()
        }

        latch.await(1000, TimeUnit.MILLISECONDS)
        return encodeAndBuildWireframe(
            bitmap = bitmap,
            isSuccess = isSuccess,
            screenshotScale = screenshotScale,
            screenshotQuality = screenshotQuality,
            viewId = viewId,
            x = x,
            y = y,
            width = width,
            height = height,
            logger = logger,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleCaptureCopyResult(
        copyResult: Int,
        bitmap: Bitmap,
        screenChanged: () -> Boolean,
        masksValid: Boolean,
        maskRects: List<android.graphics.Rect>,
        logger: (String) -> Unit,
    ): Boolean {
        if (copyResult != PixelCopy.SUCCESS) {
            bitmap.recycle()
            logger("Session Replay PixelCopy failed: $copyResult")
            return false
        }
        if (screenChanged() || !masksValid) {
            bitmap.recycle()
            return false
        }
        if (!bitmap.isValid()) {
            bitmap.recycle()
            logger("Session Replay Bitmap is invalid")
            return false
        }
        val canvas =
            try {
                Canvas(bitmap)
            } catch (e: Throwable) {
                bitmap.recycle()
                logger("Session Replay Canvas creation failed: $e")
                return false
            }
        for (rect in maskRects) {
            if (screenChanged()) {
                bitmap.recycle()
                return false
            }
            canvas.drawRoundRect(RectF(rect), 10f, 10f, maskPaint)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun encodeAndBuildWireframe(
        bitmap: Bitmap,
        isSuccess: Boolean,
        screenshotScale: Float,
        screenshotQuality: Int,
        viewId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        logger: (String) -> Unit,
    ): ReplayWireframe? {
        val scale = screenshotScale.coerceIn(0.01f, 1f)
        val quality = screenshotQuality.coerceIn(0, 100)
        val origW = bitmap.width
        val origH = bitmap.height
        // Scale before try/catch so toEncode is always defined for cleanup in catch.
        val toEncode: Bitmap =
            if (scale < 1f && bitmap.isValid()) {
                val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = bitmap.scale(w, h, true)
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        return try {
            val encW = toEncode.width
            val encH = toEncode.height
            val base64 = if (isSuccess) toEncode.webpBase64(quality) else null
            if (isSuccess) {
                logScreenshotEncodedSize(
                    logger = logger,
                    base64 = base64,
                    scale = scale,
                    origWidthPx = origW,
                    origHeightPx = origH,
                    encWidthPx = encW,
                    encHeightPx = encH,
                    quality = quality,
                )
            }
            if (toEncode.isValid()) toEncode.recycle()
            ReplayWireframe(
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
            if (toEncode.isValid()) toEncode.recycle()
            logger("Session Replay screenshot encode failed: $e")
            null
        }
    }

    /**
     * Captures a screenshot and returns the result. Suspends until PixelCopy completes.
     * The PixelCopy callback is dispatched on the internal [pixelCopyHandler] thread (single-threaded),
     * so there is no concurrent callback access.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun captureAsync(
        window: Window,
        layout: ScreenshotLayoutSnapshot,
        displayMetrics: android.util.DisplayMetrics,
        maskRects: List<android.graphics.Rect>,
        masksValid: Boolean,
        drawCountAtCollection: Long,
        currentDrawCount: () -> Long,
        logger: (String) -> Unit,
        screenshotScale: Float = 1f,
        screenshotQuality: Int = 30,
    ): ReplayWireframe? {
        if (isShutDown) return null

        val screenChanged = { currentDrawCount() != drawCountAtCollection }
        val viewId = layout.viewId
        val x = layout.locationX.densityValue(displayMetrics.density)
        val y = layout.locationY.densityValue(displayMetrics.density)
        val width = layout.widthPx.densityValue(displayMetrics.density)
        val height = layout.heightPx.densityValue(displayMetrics.density)
        val scale = screenshotScale.coerceIn(0.01f, 1f)
        val quality = screenshotQuality.coerceIn(0, 100)

        val bitmap = createBitmap(layout.widthPx, layout.heightPx, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { if (bitmap.isValid()) bitmap.recycle() }
            try {
                PixelCopy.request(window, bitmap, { copyResult ->
                    cont.resume(
                        processPixelCopyResult(
                            copyResult = copyResult,
                            bitmap = bitmap,
                            screenChanged = screenChanged,
                            masksValid = masksValid,
                            maskRects = maskRects,
                            scale = scale,
                            quality = quality,
                            viewId = viewId,
                            x = x,
                            y = y,
                            width = width,
                            height = height,
                            logger = logger,
                        ),
                    )
                }, pixelCopyHandler)
            } catch (e: Throwable) {
                if (bitmap.isValid()) bitmap.recycle()
                logger("Session Replay captureAsync failed: $e")
                cont.resume(null)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun processPixelCopyResult(
        copyResult: Int,
        bitmap: Bitmap,
        screenChanged: () -> Boolean,
        masksValid: Boolean,
        maskRects: List<android.graphics.Rect>,
        scale: Float,
        quality: Int,
        viewId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        logger: (String) -> Unit,
    ): ReplayWireframe? {
        if (isShutDown || copyResult != PixelCopy.SUCCESS || screenChanged() || !masksValid) {
            bitmap.recycle()
            if (copyResult != PixelCopy.SUCCESS) logger("Session Replay PixelCopy failed: $copyResult")
            return null
        }
        if (!bitmap.isValid()) {
            bitmap.recycle()
            logger("Session Replay Bitmap is invalid")
            return null
        }
        return try {
            val canvas = Canvas(bitmap)
            for (rect in maskRects) {
                if (screenChanged()) {
                    bitmap.recycle()
                    return null
                }
                canvas.drawRoundRect(RectF(rect), 10f, 10f, maskPaint)
            }
            val origW = bitmap.width
            val origH = bitmap.height
            val toEncode =
                if (scale < 1f && bitmap.isValid()) {
                    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    val scaled = bitmap.scale(w, h, true)
                    if (scaled !== bitmap) bitmap.recycle()
                    scaled
                } else {
                    bitmap
                }
            val encW = toEncode.width
            val encH = toEncode.height
            val base64 = toEncode.webpBase64(quality)
            logScreenshotEncodedSize(
                logger = logger,
                base64 = base64,
                scale = scale,
                origWidthPx = origW,
                origHeightPx = origH,
                encWidthPx = encW,
                encHeightPx = encH,
                quality = quality,
            )
            if (toEncode.isValid()) toEncode.recycle()
            ReplayWireframe(
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
            if (bitmap.isValid()) bitmap.recycle()
            logger("Session Replay PixelCopy callback failed: $e")
            null
        }
    }

    /**
     * Logs WebP size (KB + bytes from base64, no decode allocation), optional downscale
     * (original → encoded pixels and scale factor), and quality.
     */
    private fun logScreenshotEncodedSize(
        logger: (String) -> Unit,
        base64: String?,
        scale: Float,
        origWidthPx: Int,
        origHeightPx: Int,
        encWidthPx: Int,
        encHeightPx: Int,
        quality: Int,
    ) {
        val scaleDetail = formatScreenshotScaleDetail(scale, origWidthPx, origHeightPx, encWidthPx, encHeightPx)
        if (base64 == null) {
            logger(
                "Session Replay screenshot: WebP encode returned null ($scaleDetail, quality=$quality)",
            )
            return
        }
        val webpBytes = base64DecodedByteLength(base64)
        val webpKb = webpBytes / 1024.0
        logger(
            "Session Replay screenshot: WebP ${String.format(Locale.US, "%.2f", webpKb)} KB " +
                "($webpBytes bytes), base64 ${base64.length} chars, $scaleDetail, quality=$quality",
        )
    }

    /** e.g. `1080x2340 → 540x1170px (scale 0.50, downscaled)` or `1080x2340px (scale 1.00)`. */
    private fun formatScreenshotScaleDetail(
        scale: Float,
        origWidthPx: Int,
        origHeightPx: Int,
        encWidthPx: Int,
        encHeightPx: Int,
    ): String {
        val scaleStr = String.format(Locale.US, "%.2f", scale)
        return if (scale < 1f) {
            "${origWidthPx}x${origHeightPx} → ${encWidthPx}x${encHeightPx}px (scale $scaleStr, downscaled)"
        } else {
            "${encWidthPx}x${encHeightPx}px (scale $scaleStr)"
        }
    }

    /** Binary length represented by standard base64 without decoding. */
    private fun base64DecodedByteLength(base64: String): Int {
        val len = base64.length
        if (len == 0) {
            return 0
        }
        val padding =
            when {
                len >= 2 && base64[len - 2] == '=' -> 2
                base64[len - 1] == '=' -> 1
                else -> 0
            }
        return len * 3 / 4 - padding
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
            val transitionAlpha = v.transitionAlpha
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
