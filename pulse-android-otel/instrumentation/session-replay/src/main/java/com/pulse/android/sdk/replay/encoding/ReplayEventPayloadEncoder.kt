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

internal object ReplayEventPayloadEncoder {
    fun encodeToJson(events: List<ReplayEvent>): String = ReplayGson.instance.toJson(toTransportEvents(events))

    internal fun toTransportEvents(events: List<ReplayEvent>): List<TransportReplayEvent> = events.map { it.toTransport() }

    private fun ReplayEvent.toTransport(): TransportReplayEvent =
        TransportReplayEvent(
            type = type.value,
            timestamp = timestamp,
            data = data?.toTransport(),
        )

    private fun ReplayEventData.toTransport(): TransportEventData =
        when (this) {
            is ReplayMetaData -> {
                TransportMetaData(href = href, width = width, height = height)
            }
            is ReplayFullSnapshotData -> {
                TransportFullSnapshotData(
                    wireframes = wireframes.map { it.toTransport() },
                    initialOffset =
                        TransportOffset(
                            top = initialOffsetTop,
                            left = initialOffsetLeft,
                        ),
                )
            }
            is ReplayIncrementalMutationData -> {
                toTransportMutation()
            }
            is ReplayIncrementalMouseInteractionData -> {
                toTransportMouseInteraction()
            }
            is ReplayCustomEventData -> {
                TransportCustomEventData(tag = tag, payload = payload)
            }
        }

    private fun ReplayIncrementalMutationData.toTransportMutation() =
        TransportIncrementalMutationData(
            source = source.value,
            adds =
                adds?.map {
                    TransportMutatedNode(
                        parentId = it.parentId,
                        wireframe = it.wireframe.toTransport(),
                    )
                },
            removes =
                removes?.map {
                    TransportRemovedNode(id = it.id, parentId = it.parentId)
                },
            updates =
                updates?.map {
                    TransportMutatedNode(
                        parentId = it.parentId,
                        wireframe = it.wireframe.toTransport(),
                    )
                },
        )

    private fun ReplayIncrementalMouseInteractionData.toTransportMouseInteraction() =
        TransportMouseInteractionData(
            id = id,
            type = type.value,
            x = x,
            y = y,
            source = source.value,
            pointerType = pointerType,
            positions =
                positions?.map {
                    TransportMousePosition(
                        x = it.x,
                        y = it.y,
                        id = it.id,
                        timeOffset = it.timeOffset,
                    )
                },
        )

    private fun ReplayWireframe.toTransport(): TransportWireframe =
        TransportWireframe(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            type = type,
            text = text,
            base64 = base64,
            inputType = inputType,
            value = value,
            isDisabled = isDisabled,
            isChecked = isChecked,
            label = label,
            parentId = parentId,
            max = max,
            style = style?.toTransport(),
            childWireframes = childWireframes?.map { it.toTransport() },
            options = options,
        )

    private fun ReplayStyle.toTransport(): TransportStyle =
        TransportStyle(
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
}
