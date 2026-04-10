package com.pulse.android.sdk.replay.internal.pipeline

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayFullSnapshotEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.android.sdk.replay.events.ReplayIncrementalSnapshotEvent
import com.pulse.android.sdk.replay.events.ReplayMetaEvent
import com.pulse.android.sdk.replay.events.ReplayMutatedNode
import com.pulse.android.sdk.replay.events.ReplayRemovedNode
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.internal.scheduling.ViewTreeSnapshotStatus

/**
 * Builds replay events from a captured wireframe and current status; computes full vs incremental.
 */
internal object SnapshotPipeline {
    fun generateEvents(
        wireframe: ReplayWireframe,
        status: ViewTreeSnapshotStatus,
        timestamp: Long,
        view: View,
        screenWidth: Int,
        screenHeight: Int,
    ): List<ReplayEvent> {
        val events = mutableListOf<ReplayEvent>()

        if (!status.hasSentMetaEvent) {
            val title = view.getScreenTitle()
            events.add(ReplayMetaEvent(screenWidth, screenHeight, timestamp, title))
            status.hasSentMetaEvent = true
        }

        if (!status.hasSentFullSnapshot) {
            events.add(
                ReplayFullSnapshotEvent(
                    wireframes = listOf(wireframe),
                    initialOffsetTop = 0,
                    initialOffsetLeft = 0,
                    timestamp = timestamp,
                ),
            )
            status.hasSentFullSnapshot = true
        } else {
            val lastSnapshot = status.lastSnapshot
            val lastList = if (lastSnapshot != null) listOf(lastSnapshot) else emptyList()
            val (added, removed, updated) =
                findAddedRemovedUpdated(
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

        return events
    }

    /** Compares wireframe fields except [ReplayWireframe.childWireframes] (used for incremental diff). */
    private fun ReplayWireframe.equalsIgnoringChildren(other: ReplayWireframe): Boolean =
        id == other.id &&
            x == other.x &&
            y == other.y &&
            width == other.width &&
            height == other.height &&
            type == other.type &&
            inputType == other.inputType &&
            text == other.text &&
            label == other.label &&
            value == other.value &&
            base64 == other.base64 &&
            style == other.style &&
            isDisabled == other.isDisabled &&
            isChecked == other.isChecked &&
            options == other.options &&
            parentId == other.parentId &&
            max == other.max

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
            val oldRaw = oldMap[id]
            val newRaw = newMap[id]
            if (oldRaw != null && newRaw != null && !oldRaw.equalsIgnoringChildren(newRaw)) {
                updated.add(newRaw)
            }
        }
        return Triple(added, removed, updated)
    }

    private fun View.getScreenTitle(): String =
        context
            .getActivity()
            ?.run {
                title?.run { toString().substringAfter("/") }
            }.orEmpty()
}

private tailrec fun Context.getActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> null
    }
