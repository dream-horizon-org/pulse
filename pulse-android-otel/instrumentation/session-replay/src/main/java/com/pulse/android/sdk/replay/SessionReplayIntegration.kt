package com.pulse.android.sdk.replay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.Window
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import curtains.TouchEventInterceptor
import curtains.onDecorViewReady
import curtains.phoneWindow
import curtains.touchEventInterceptors
import curtains.windowAttachCount
import com.pulse.android.sdk.replay.internal.capture.MaskingCollector
import com.pulse.android.sdk.replay.internal.capture.ScreenshotCapture
import com.pulse.android.sdk.replay.internal.capture.WireframeCapture
import com.pulse.android.sdk.replay.internal.pipeline.SnapshotPipeline
import com.pulse.android.sdk.replay.internal.scheduling.NextDrawListener
import com.pulse.android.sdk.replay.internal.scheduling.ViewTreeSnapshotStatus
import com.pulse.android.sdk.replay.internal.scheduling.isAliveAndAttachedToWindow
import com.pulse.android.sdk.replay.internal.scheduling.NextDrawListener.Companion.onNextDraw
import com.pulse.android.sdk.replay.internal.util.DefaultDateProvider
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionEvent
import com.pulse.android.sdk.replay.events.ReplayMouseInteraction
import com.pulse.android.sdk.replay.events.ScreenSizeInfo
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors

/**
 * Session Replay integration: mirrors PostHog Android (Curtains, touch events, screenshot + wireframe).
 * Implements [SessionReplayController]. Install via [install]; provide [ReplayEventEmitter] to receive events.
 */
public class SessionReplayIntegration(
    private val context: Context,
    private val config: SessionReplayConfig,
    private val eventEmitter: ReplayEventEmitter,
    private val logger: (String) -> Unit = {},
    mainHandler: android.os.Handler? = null,
) : SessionReplayController {

    private val mainHandler = mainHandler ?: android.os.Handler(android.os.Looper.getMainLooper())
    private val dateProvider = DefaultDateProvider()
    private val decorViews = WeakHashMap<View, ViewTreeSnapshotStatus>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "PulseReplayThread").apply { isDaemon = true }
    }
    private val displayMetrics = context.resources.displayMetrics

    @Volatile
    private var isSessionReplayActive = false

    @Volatile
    private var isOnDrawnCalled = false

    @Volatile
    private var currentSessionId: String? = null

    private fun onDrawCallback() { isOnDrawnCalled = true }

    private fun addView(view: View, added: Boolean) {
        try {
            view.phoneWindow?.let { window ->
                var hasDecorView = false
                window.peekDecorView()?.let { decorView ->
                    hasDecorView = decorViews[decorView] != null
                }
                if (added) {
                    if (view.windowAttachCount == 0 || !hasDecorView) {
                        window.onDecorViewReady { decorView ->
                            try {
                                val listener = decorView.onNextDraw(
                                    mainHandler,
                                    dateProvider,
                                    config.throttleDelayMs,
                                    ::onDrawCallback,
                                ) {
                                    if (!isActive()) return@onNextDraw
                                    executor.submit {
                                        try {
                                            generateSnapshot(WeakReference(decorView), WeakReference(window))
                                        } catch (e: Throwable) {
                                            logger("Session Replay generateSnapshot failed: $e")
                                        }
                                    }
                                }
                                decorViews[decorView] = ViewTreeSnapshotStatus(listener)
                            } catch (e: Throwable) {
                                logger("Session Replay onDecorViewReady failed: $e")
                            }
                        }
                        window.touchEventInterceptors += onTouchEventListener
                    }
                } else {
                    window.peekDecorView()?.let { decorView ->
                        decorViews[decorView]?.let { status ->
                            clearViewListeners(decorView, status)
                        }
                    }
                }
                Unit
            }
        } catch (e: Throwable) {
            logger("Session Replay OnRootViewsChangedListener failed: $e")
        }
    }

    private val onRootViewsChangedListener = OnRootViewsChangedListener { view, added ->
        addView(view, added)
    }

    private val onTouchEventListener = TouchEventInterceptor { motionEvent, dispatch ->
        val timestamp = dateProvider.currentTimeMillis()
        try {
            val state = dispatch(motionEvent)
            try {
                val safeMotionEvent = MotionEvent.obtain(motionEvent)
                executor.submit {
                    try {
                        if (!isActive()) return@submit
                        when (safeMotionEvent.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> generateMouseInteractions(timestamp, safeMotionEvent, ReplayMouseInteraction.TouchStart)
                            MotionEvent.ACTION_UP -> generateMouseInteractions(timestamp, safeMotionEvent, ReplayMouseInteraction.TouchEnd)
                        }
                    } catch (e: Throwable) {
                        logger("Executor#OnTouchEventListener $safeMotionEvent failed: $e")
                    } finally {
                        safeMotionEvent.recycle()
                    }
                }
                state
            } catch (_: Throwable) {
                state
            }
        } catch (e: Throwable) {
            logger("TouchEventInterceptor $motionEvent failed: $e")
            throw e
        }
    }

    private fun generateMouseInteractions(
        timestamp: Long,
        motionEvent: MotionEvent,
        type: ReplayMouseInteraction,
    ) {
        val events = mutableListOf<ReplayEvent>()
        for (index in 0 until motionEvent.pointerCount) {
            try {
                val id = motionEvent.getPointerId(index)
                val absX = (motionEvent.getRawXCompat(index).toInt() / displayMetrics.density).toInt()
                val absY = (motionEvent.getRawYCompat(index).toInt() / displayMetrics.density).toInt()
                val data = ReplayIncrementalMouseInteractionData(id = id, type = type, x = absX, y = absY)
                events.add(ReplayIncrementalMouseInteractionEvent(data, timestamp))
            } catch (e: Throwable) {
                logger("Reading MotionEvent pointers failed: $e")
            }
        }
        if (events.isNotEmpty()) eventEmitter.emit(requireSessionId(), events)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun MotionEvent.getRawXCompat(index: Int): Float {
        return if (index < 0 || index >= pointerCount) rawX
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) getRawX(index)
        else rawX
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun MotionEvent.getRawYCompat(index: Int): Float {
        return if (index < 0 || index >= pointerCount) rawY
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) getRawY(index)
        else rawY
    }

    private fun clearViewListeners(view: View, status: ViewTreeSnapshotStatus) {
        if (view.isAliveAndAttachedToWindow()) {
            mainHandler.post {
                if (view.isAliveAndAttachedToWindow()) {
                    try {
                        view.viewTreeObserver?.removeOnDrawListener(status.listener)
                    } catch (_: Throwable) {}
                }
            }
        }
        view.phoneWindow?.let { window ->
            window.touchEventInterceptors -= onTouchEventListener
        }
        decorViews.remove(view)
    }

    private fun generateSnapshot(viewRef: WeakReference<View>, windowRef: WeakReference<Window>) {
        val view = viewRef.get() ?: return
        val status = decorViews[view] ?: return
        val window = windowRef.get() ?: return

        val timestamp = dateProvider.currentTimeMillis()

        val wireframe = if (config.screenshot) {
            ScreenshotCapture.capture(
                window = window,
                view = view,
                displayMetrics = displayMetrics,
                getMaskRects = { v, rects ->
                    MaskingCollector.findMaskableWidgets(
                        v,
                        config,
                        rects,
                        onDrawCalled = { isOnDrawnCalled },
                        logger = logger,
                    )
                },
                onDrawFlag = { isOnDrawnCalled },
                setOnDrawFlag = { isOnDrawnCalled = it },
                logger = logger,
                screenshotScale = config.screenshotScale,
                screenshotQuality = config.screenshotQuality,
            )
        } else {
            WireframeCapture.toWireframe(
                view = view,
                config = config,
                displayMetrics = displayMetrics,
                logger = logger,
            )
        }

        val wireframeOrNull = wireframe ?: return

        if (!config.screenshot && wireframeOrNull.style?.backgroundColor == null) {
            context.theme?.let { theme ->
                WireframeCapture.themeToRGBColor(theme)?.let {
                    wireframeOrNull.style?.backgroundColor = it
                }
            }
        }

        val screenSize = context.screenSize()
        val screenWidth = screenSize?.width ?: (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val screenHeight = screenSize?.height ?: (displayMetrics.heightPixels / displayMetrics.density).toInt()

        val events = SnapshotPipeline.generateEvents(
            wireframe = wireframeOrNull,
            status = status,
            config = config,
            timestamp = timestamp,
            view = view,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            dateProvider = dateProvider,
        )

        if (events.isNotEmpty()) {
            eventEmitter.emit(requireSessionId(), events)
        }

        status.lastSnapshot = wireframeOrNull
    }

    private fun resetViewSnapshotStates(status: ViewTreeSnapshotStatus) {
        status.sentFullSnapshot = false
        status.sentMetaEvent = false
        status.keyboardVisible = false
        status.lastSnapshot = null
    }

    private fun clearSnapshotStates() {
        decorViews.values.forEach { resetViewSnapshotStates(it) }
    }

    public fun install() {
        Curtains.rootViews.forEach { addView(it, true) }
        try {
            Curtains.onRootViewsChangedListeners += onRootViewsChangedListener
        } catch (e: Throwable) {
            logger("Session Replay setup failed: $e")
        }
    }

    public fun uninstall() {
        try {
            Curtains.onRootViewsChangedListeners -= onRootViewsChangedListener
            decorViews.entries.toList().forEach { (view, status) -> clearViewListeners(view, status) }
        } catch (e: Throwable) {
            logger("Session Replay uninstall failed: $e")
        }
        isSessionReplayActive = false
        isOnDrawnCalled = false
        clearSnapshotStates()
        decorViews.clear()
    }

    override fun start(resumeCurrent: Boolean) {
        if (!resumeCurrent) {
            clearSnapshotStates()
            currentSessionId = UUID.randomUUID().toString()
        }
        isSessionReplayActive = true
    }

    private fun requireSessionId(): String =
        currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it }

    override fun stop() {
        isSessionReplayActive = false
        isOnDrawnCalled = false
    }

    override fun isActive(): Boolean = isSessionReplayActive
}

private fun Context.screenSize(): ScreenSizeInfo? {
    return com.pulse.android.sdk.replay.internal.util.screenSize(this)
}
