package com.pulse.android.sdk.replay.encoding

import com.pulse.android.sdk.replay.events.ReplayCustomEventData
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayEventData
import com.pulse.android.sdk.replay.events.ReplayFullSnapshotData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.android.sdk.replay.events.ReplayMetaData
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.models.PulseReplayCustomEventData
import com.pulse.android.sdk.replay.models.PulseReplayEventData
import com.pulse.android.sdk.replay.models.PulseReplayFullSnapshotData
import com.pulse.android.sdk.replay.models.PulseReplayIncrementalMutationData
import com.pulse.android.sdk.replay.models.PulseReplayJson
import com.pulse.android.sdk.replay.models.PulseReplayMetaData
import com.pulse.android.sdk.replay.models.PulseReplayMouseInteractionData
import com.pulse.android.sdk.replay.models.PulseReplayMousePosition
import com.pulse.android.sdk.replay.models.PulseReplayMutatedNode
import com.pulse.android.sdk.replay.models.PulseReplayOffset
import com.pulse.android.sdk.replay.models.PulseReplayRemovedNode
import com.pulse.android.sdk.replay.models.PulseReplaySnapshotEvent
import com.pulse.android.sdk.replay.models.PulseReplayStyle
import com.pulse.android.sdk.replay.models.PulseReplayWireframe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal object ReplayEventPayloadEncoder {
    fun encodeToJson(events: List<ReplayEvent>): String = PulseReplayJson.instance.encodeToString(toPulseReplayWireEvents(events))

    internal fun toPulseReplayWireEvents(events: List<ReplayEvent>): List<PulseReplaySnapshotEvent> = events.map { it.toPulseReplayWire() }

    private fun ReplayEvent.toPulseReplayWire(): PulseReplaySnapshotEvent =
        PulseReplaySnapshotEvent(
            type = type.value,
            timestamp = timestamp,
            data = data?.toPulseReplayWire(),
        )

    private fun ReplayEventData.toPulseReplayWire(): PulseReplayEventData =
        when (this) {
            is ReplayMetaData -> {
                val g = gcd(width, height)
                PulseReplayMetaData(
                    href = href,
                    width = width,
                    height = height,
                    aspectRatio = "${width / g}:${height / g}",
                )
            }
            is ReplayFullSnapshotData -> {
                PulseReplayFullSnapshotData(
                    wireframes = wireframes.map { it.toPulseReplayWire() },
                    initialOffset =
                        PulseReplayOffset(
                            top = initialOffsetTop,
                            left = initialOffsetLeft,
                        ),
                )
            }
            is ReplayIncrementalMutationData -> {
                toPulseReplayMutationWire()
            }
            is ReplayIncrementalMouseInteractionData -> {
                toPulseReplayMouseInteractionWire()
            }
            is ReplayCustomEventData -> {
                PulseReplayCustomEventData(tag = tag, payload = payload.toJsonObject())
            }
        }

    private fun ReplayIncrementalMutationData.toPulseReplayMutationWire() =
        PulseReplayIncrementalMutationData(
            source = source.value,
            adds =
                adds?.map {
                    PulseReplayMutatedNode(
                        parentId = it.parentId,
                        wireframe = it.wireframe.toPulseReplayWire(),
                    )
                },
            removes =
                removes?.map {
                    PulseReplayRemovedNode(id = it.id, parentId = it.parentId)
                },
            updates =
                updates?.map {
                    PulseReplayMutatedNode(
                        parentId = it.parentId,
                        wireframe = it.wireframe.toPulseReplayWire(),
                    )
                },
        )

    private fun ReplayIncrementalMouseInteractionData.toPulseReplayMouseInteractionWire() =
        PulseReplayMouseInteractionData(
            id = id,
            type = type.value,
            x = x,
            y = y,
            source = source.value,
            pointerType = pointerType.value,
            positions =
                positions?.map {
                    PulseReplayMousePosition(
                        x = it.x,
                        y = it.y,
                        id = it.id,
                        timeOffset = it.timeOffset,
                    )
                },
        )

    private fun ReplayWireframe.toPulseReplayWire(): PulseReplayWireframe =
        PulseReplayWireframe(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            type = type,
            text = text,
            base64 = base64,
            inputType = inputType,
            value = value?.toJsonElement(),
            isDisabled = isDisabled,
            isChecked = isChecked,
            label = label,
            parentId = parentId,
            max = max,
            style = style?.toPulseReplayWire(),
            childWireframes = childWireframes?.map { it.toPulseReplayWire() },
            options = options,
        )

    private fun ReplayStyle.toPulseReplayWire(): PulseReplayStyle =
        PulseReplayStyle(
            color = color,
            backgroundColor = backgroundColor,
            backgroundImage = backgroundImage,
            borderWidth = borderWidth,
            borderRadius = borderRadius,
            borderColor = borderColor,
            fontSize = fontSize,
            fontFamily = fontFamily,
            horizontalAlign = horizontalAlign,
            verticalAlign = verticalAlign,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom,
            paddingLeft = paddingLeft,
            paddingRight = paddingRight,
            bar = bar,
            iconLeft = iconLeft,
            iconRight = iconRight,
        )

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun Map<String, Any>.toJsonObject(): JsonObject =
        buildJsonObject {
            for ((key, value) in this@toJsonObject) {
                put(key, value.toJsonElement())
            }
        }

    private fun Any?.toJsonElement(): JsonElement =
        when (this) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            else -> JsonPrimitive(toString())
        }
}
