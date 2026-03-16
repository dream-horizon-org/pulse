package com.pulse.android.sdk.replay.internal.pipeline

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pulse.android.sdk.replay.SessionReplayConfig
import com.pulse.android.sdk.replay.events.ReplayCustomEvent
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayFullSnapshotEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.android.sdk.replay.events.ReplayIncrementalSnapshotEvent
import com.pulse.android.sdk.replay.events.ReplayMetaEvent
import com.pulse.android.sdk.replay.events.ReplayMutatedNode
import com.pulse.android.sdk.replay.events.ReplayRemovedNode
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.internal.scheduling.ViewTreeSnapshotStatus
import com.pulse.android.sdk.replay.internal.util.DateProvider

/**
 * Builds replay events from a captured wireframe and current status; computes full vs incremental.
 */
internal object SnapshotPipeline {

    fun generateEvents(
        wireframe: ReplayWireframe,
        status: ViewTreeSnapshotStatus,
        config: SessionReplayConfig,
        timestamp: Long,
        view: View,
        screenWidth: Int,
        screenHeight: Int,
        dateProvider: DateProvider,
    ): List<ReplayEvent> {
        val events = mutableListOf<ReplayEvent>()

        if (!status.sentMetaEvent) {
            val title = view.getScreenTitle()
            events.add(ReplayMetaEvent(screenWidth, screenHeight, timestamp, title))
            status.sentMetaEvent = true
        }

        if (!status.sentFullSnapshot) {
            events.add(
                ReplayFullSnapshotEvent(
                    wireframes = listOf(wireframe),
                    initialOffsetTop = 0,
                    initialOffsetLeft = 0,
                    timestamp = timestamp,
                ),
            )
            status.sentFullSnapshot = true
        } else {
            val lastSnapshot = status.lastSnapshot
            val lastList = if (lastSnapshot != null) listOf(lastSnapshot) else emptyList()
            val (added, removed, updated) = findAddedRemovedUpdated(
                lastList.flattenChildren(),
                listOf(wireframe).flattenChildren(),
            )
            if (added.isNotEmpty() || removed.isNotEmpty() || updated.isNotEmpty()) {
                val adds = added.map { ReplayMutatedNode(it, it.parentId) }
                val removes = removed.map { ReplayRemovedNode(it.id, it.parentId) }
                val updates = updated.map { ReplayMutatedNode(it, it.parentId) }
                events.add(
                    ReplayIncrementalSnapshotEvent(
                        ReplayIncrementalMutationData(adds = adds, removes = removes, updates = updates),
                        timestamp,
                    ),
                )
            }
        }

        // Custom events (e.g. keyboard) disabled for now
        // val (keyboardVisible, keyboardEvent) = detectKeyboardVisibility(view, status.keyboardVisible, timestamp)
        // status.keyboardVisible = keyboardVisible
        // keyboardEvent?.let { events.add(it) }

        return events
    }

    private fun detectKeyboardVisibility(
        view: View,
        currentVisible: Boolean,
        timestamp: Long,
    ): Pair<Boolean, ReplayCustomEvent?> {
        val insets = ViewCompat.getRootWindowInsets(view) ?: return Pair(currentVisible, null)
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        if (currentVisible == imeVisible) return Pair(currentVisible, null)
        val payload = mutableMapOf<String, Any>()
        if (imeVisible) {
            payload["open"] = true
            payload["height"] = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            payload["open"] = false
        }
        return Pair(imeVisible, ReplayCustomEvent("keyboard", payload, timestamp))
    }

    private fun List<ReplayWireframe>.flattenChildren(): List<ReplayWireframe> {
        val result = mutableListOf<ReplayWireframe>()
        for (item in this) {
            result.add(item)
            item.childWireframes?.let { result.addAll(it.flattenChildren()) }
        }
        return result
    }

    private fun findAddedRemovedUpdated(
        oldItems: List<ReplayWireframe>,
        newItems: List<ReplayWireframe>,
    ): Triple<List<ReplayWireframe>, List<ReplayWireframe>, List<ReplayWireframe>> {
        val oldMap = oldItems.associateBy { it.id }
        val newMap = newItems.associateBy { it.id }
        val oldIds = oldItems.map { it.id }.toSet()
        val newIds = newItems.map { it.id }.toSet()
        val added = newItems.filter { it.id !in oldIds }
        val removed = oldItems.filter { it.id !in newIds }
        val updated = mutableListOf<ReplayWireframe>()
        for (id in oldIds intersect newIds) {
            val oldItem = oldMap[id]?.copy(childWireframes = null) ?: continue
            val newItem = newMap[id]?.copy(childWireframes = null) ?: continue
            if (oldItem != newItem) updated.add(newMap[id]!!)
        }
        return Triple(added, removed, updated)
    }

    private fun View.getScreenTitle(): String =
        (context as? android.app.Activity)?.title?.toString()?.substringAfter("/") ?: ""
}
