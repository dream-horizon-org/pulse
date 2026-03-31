package com.pulse.android.sdk.replay

import android.content.Context
import android.view.View
import android.view.Window
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.events.ScreenSizeInfo
import com.pulse.android.sdk.replay.internal.capture.MaskRectCache
import com.pulse.android.sdk.replay.internal.capture.ScreenshotCapture
import com.pulse.android.sdk.replay.internal.capture.ScreenshotLayoutSnapshot
import com.pulse.android.sdk.replay.internal.capture.WireframeCapture
import com.pulse.android.sdk.replay.internal.capture.collectScreenshotLayout
import com.pulse.android.sdk.replay.internal.pipeline.SnapshotPipeline
import com.pulse.android.sdk.replay.internal.scheduling.NextDrawListener.Companion.onNextDraw
import com.pulse.android.sdk.replay.internal.scheduling.ViewTreeSnapshotStatus
import com.pulse.android.sdk.replay.internal.scheduling.isAliveAndAttachedToWindow
import com.pulse.utils.PulseOtelUtils
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import curtains.onDecorViewReady
import curtains.phoneWindow
import curtains.windowAttachCount
import io.opentelemetry.sdk.common.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Session Replay integration: mirrors PostHog Android (Curtains, touch events, screenshot + wireframe).
 * Implements [SessionReplayController]. Install via [install]; provide [ReplayEventEmitter] to receive events.
 *
 * @param sessionIdProvider Supplies the session ID for each batch (e.g. from RUM [SessionProvider]).
 *   Replay batches use this ID so they align with the same session as other telemetry.
 */
public class SessionReplayIntegration(
    private val context: Context,
    private val config: SessionReplayConfig,
    private val eventEmitter: ReplayEventEmitter,
    private val sessionIdProvider: () -> String,
    private val screenNameProvider: () -> String,
) : SessionReplayController {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val logger: (String) -> Unit = { msg ->
        PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) { msg }
    }
    private val clock: Clock = Clock.getDefault()
    private val decorViews: MutableMap<View, ViewTreeSnapshotStatus> =
        Collections.synchronizedMap(WeakHashMap())

    @Suppress("InjectDispatcher")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val displayMetrics = context.resources.displayMetrics

    @Volatile
    private var isSessionReplayActive = false

    private val drawCounter = AtomicLong(0)

    private fun onDrawCallback() {
        drawCounter.incrementAndGet()
        synchronized(decorViews) {
            decorViews.values.forEach { it.maskRectCache.invalidate() }
        }
    }

    private fun addView(
        view: View,
        added: Boolean,
    ) {
        try {
            val window = view.phoneWindow ?: return
            val hasDecorView = window.peekDecorView()?.let { decorViews[it] != null } == true
            if (added) {
                if (view.windowAttachCount == 0 || !hasDecorView) {
                    window.onDecorViewReady { decorView ->
                        setupDecorViewCapture(decorView, window)
                    }
                }
            } else {
                window.peekDecorView()?.let { decorView ->
                    decorViews[decorView]?.let { status ->
                        clearViewListeners(decorView, status)
                    }
                }
            }
        } catch (e: Throwable) {
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) { "Session Replay OnRootViewsChangedListener failed: $e" }
        }
    }

    private fun setupDecorViewCapture(
        decorView: View,
        window: Window,
    ) {
        try {
            val viewMaskCache = MaskRectCache(config, logger)
            viewMaskCache.registerListeners(decorView)
            val listener =
                decorView.onNextDraw(
                    mainHandler,
                    clock,
                    config.effectiveThrottleDelayMs,
                    ::onDrawCallback,
                ) {
                    if (!isActive()) return@onNextDraw
                    decorView.post {
                        try {
                            if (!isActive() || !decorView.isAliveAndAttachedToWindow()) return@post
                            val countAtCollection = drawCounter.get()
                            viewMaskCache.collectIfNeeded(
                                decorView,
                                onDrawCalled = { drawCounter.get() != countAtCollection },
                            )
                            val snapshotMasks = ArrayList(viewMaskCache.rects)
                            val isSnapshotMasksValid = viewMaskCache.isValid
                            val screenshotLayout: ScreenshotLayoutSnapshot? =
                                if (config.isScreenshot) {
                                    collectScreenshotLayout(decorView, logger)
                                } else {
                                    null
                                }
                            scope.launch {
                                generateSnapshot(
                                    WeakReference(decorView),
                                    WeakReference(window),
                                    snapshotMasks,
                                    isSnapshotMasksValid,
                                    countAtCollection,
                                    screenshotLayout,
                                )
                            }
                        } catch (e: Throwable) {
                            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) { "Session Replay mask collection failed: $e" }
                        }
                    }
                }
            decorViews[decorView] = ViewTreeSnapshotStatus(listener, viewMaskCache)
        } catch (e: Throwable) {
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) { "Session Replay setupDecorViewCapture failed: $e" }
        }
    }

    private val onRootViewsChangedListener =
        OnRootViewsChangedListener { view, added ->
            addView(view, added)
        }

    private fun clearViewListeners(
        view: View,
        status: ViewTreeSnapshotStatus,
    ) {
        status.maskRectCache.unregisterListeners()
        status.maskRectCache.clear()
        if (view.isAliveAndAttachedToWindow()) {
            mainHandler.post {
                if (view.isAliveAndAttachedToWindow()) {
                    view.viewTreeObserver?.takeIf { it.isAlive }?.removeOnDrawListener(status.listener)
                }
            }
        }
        // Touch/mouse events disabled for now
        // view.phoneWindow?.let { window ->
        //     window.touchEventInterceptors -= onTouchEventListener
        // }
        decorViews.remove(view)
    }

    private suspend fun generateSnapshot(
        viewRef: WeakReference<View>,
        windowRef: WeakReference<Window>,
        preCollectedMasks: List<android.graphics.Rect>,
        masksValid: Boolean,
        drawCountAtCollection: Long,
        screenshotLayout: ScreenshotLayoutSnapshot?,
    ) {
        val view = viewRef.get() ?: return
        if (view !in decorViews) return
        val window = windowRef.get() ?: return

        val timestamp = TimeUnit.NANOSECONDS.toMillis(clock.now())

        val wireframe =
            if (config.isScreenshot) {
                val layout = screenshotLayout ?: return
                ScreenshotCapture.captureAsync(
                    window = window,
                    layout = layout,
                    displayMetrics = displayMetrics,
                    maskRects = preCollectedMasks,
                    masksValid = masksValid,
                    drawCountAtCollection = drawCountAtCollection,
                    currentDrawCount = { drawCounter.get() },
                    logger = logger,
                    screenshotScale = config.effectiveScreenshotScale,
                    screenshotQuality = config.effectiveScreenshotQuality,
                )
            } else {
                WireframeCapture.toWireframe(
                    view = view,
                    config = config,
                    displayMetrics = displayMetrics,
                    logger = logger,
                )
            }
        wireframe?.let { generateSnapshotWithWireframe(it, viewRef, timestamp) }
    }

    private fun generateSnapshotWithWireframe(
        wireframeOrNull: ReplayWireframe,
        viewRef: WeakReference<View>,
        timestamp: Long,
    ) {
        val view = viewRef.get() ?: return
        val status = decorViews[view] ?: return

        val screenSize = context.screenSize()
        val screenWidth = screenSize?.width ?: (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val screenHeight = screenSize?.height ?: (displayMetrics.heightPixels / displayMetrics.density).toInt()

        val events =
            SnapshotPipeline.generateEvents(
                wireframe = wireframeOrNull,
                status = status,
                timestamp = timestamp,
                screenName = screenNameProvider().takeIf { it.isNotBlank() } ?: "unknown",
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )

        if (events.isNotEmpty()) {
            eventEmitter.emit(requireSessionId(), events)
        }

        status.lastSnapshot = wireframeOrNull
    }

    private fun resetViewSnapshotStates(status: ViewTreeSnapshotStatus) {
        status.hasSentFullSnapshot = false
        status.hasSentMetaEvent = false
        status.isKeyboardVisible = false
        status.lastSnapshot = null
        status.maskRectCache.clear()
    }

    private fun clearSnapshotStates() {
        synchronized(decorViews) {
            decorViews.values.forEach { resetViewSnapshotStates(it) }
        }
    }

    public fun install() {
        Curtains.rootViews.forEach { addView(it, true) }
        try {
            Curtains.onRootViewsChangedListeners += onRootViewsChangedListener
        } catch (e: Throwable) {
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) { "Session Replay setup failed: $e" }
        }
    }

    public fun uninstall() {
        try {
            Curtains.onRootViewsChangedListeners -= onRootViewsChangedListener
            val snapshot = synchronized(decorViews) { decorViews.entries.toList() }
            snapshot.forEach { (view, status) -> clearViewListeners(view, status) }
        } catch (e: Throwable) {
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) { "Session Replay uninstall failed: $e" }
        }
        isSessionReplayActive = false
        drawCounter.incrementAndGet()
        clearSnapshotStates()
        decorViews.clear()
        (eventEmitter as? PersistingReplayEmitter)?.shutdown()
        ScreenshotCapture.shutdown()
        scope.cancel()
    }

    /** Flushes any pending replay batches (e.g. before shutdown). */
    public fun flush() {
        eventEmitter.flush()
    }

    override fun start(resumeCurrent: Boolean) {
        if (!resumeCurrent) {
            clearSnapshotStates()
        }
        isSessionReplayActive = true
    }

    private fun requireSessionId(): String {
        val id = sessionIdProvider()
        return id.ifEmpty { UUID.randomUUID().toString() }
    }

    override fun stop() {
        isSessionReplayActive = false
        drawCounter.incrementAndGet()
    }

    override fun isActive(): Boolean = isSessionReplayActive
}

private fun Context.screenSize(): ScreenSizeInfo? =
    com.pulse.android.sdk.replay.internal.util
        .screenSize(this)
