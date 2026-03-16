package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import com.pulse.android.sdk.replay.SessionReplayConfig
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Caches mask rects collected on the main thread and invalidates on scroll/layout changes.
 *
 * Static screens reuse the cached rects (zero main-thread cost). When a scroll or layout
 * change occurs the cache is marked dirty and the next snapshot re-collects on the main thread.
 */
internal class MaskRectCache(
    private val config: SessionReplayConfig,
    private val logger: (String) -> Unit,
) {

    @Volatile
    var rects: List<Rect> = emptyList()
        private set

    @Volatile
    var valid: Boolean = false
        private set

    private val dirty = AtomicBoolean(true)
    private var registeredView: WeakReference<View>? = null

    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        dirty.set(true)
    }

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        dirty.set(true)
    }

    fun isDirty(): Boolean = dirty.get()

    /**
     * Collect mask rects if the cache is dirty. Must be called on the main thread.
     * Returns true if fresh rects were collected, false if cached rects are still valid.
     */
    fun collectIfNeeded(
        view: View,
        onDrawCalled: () -> Boolean,
    ): Boolean {
        if (!dirty.getAndSet(false)) return false

        val collected = mutableListOf<Rect>()
        val masksValid = MaskingCollector.findMaskableWidgets(
            view,
            config,
            collected,
            onDrawCalled = onDrawCalled,
            logger = logger,
        )
        rects = collected
        valid = masksValid
        return true
    }

    fun registerListeners(view: View) {
        unregisterListeners()
        registeredView = WeakReference(view)
        try {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.addOnScrollChangedListener(scrollChangedListener)
                observer.addOnGlobalLayoutListener(globalLayoutListener)
            }
        } catch (e: Throwable) {
            logger("MaskRectCache registerListeners failed: $e")
        }
    }

    fun unregisterListeners() {
        val view = registeredView?.get() ?: return
        try {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnScrollChangedListener(scrollChangedListener)
                observer.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        } catch (_: Throwable) {
            // View or observer already dead
        }
        registeredView = null
    }

    fun invalidate() {
        dirty.set(true)
    }

    fun clear() {
        dirty.set(true)
        rects = emptyList()
        valid = false
    }
}
