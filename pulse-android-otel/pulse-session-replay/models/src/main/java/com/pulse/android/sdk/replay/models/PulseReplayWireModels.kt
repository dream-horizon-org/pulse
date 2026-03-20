package com.pulse.android.sdk.replay.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** kotlinx.serialization Json instance for serializing [PulseReplayEnvelope] and nested wire types. */
public object PulseReplayJson {
    public val instance: Json = Json {
        encodeDefaults = false
        explicitNulls = false
    }
}

@Serializable
public data class PulseReplaySnapshotEvent(
    public val type: Int,
    public val timestamp: Long,
    public val data: PulseReplayEventData? = null,
)

@Serializable(with = PulseReplayEventDataSerializer::class)
public sealed class PulseReplayEventData

internal object PulseReplayEventDataSerializer : KSerializer<PulseReplayEventData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PulseReplayEventData")

    override fun serialize(encoder: Encoder, value: PulseReplayEventData) {
        when (value) {
            is PulseReplayMetaData -> PulseReplayMetaData.serializer().serialize(encoder, value)
            is PulseReplayFullSnapshotData -> PulseReplayFullSnapshotData.serializer().serialize(encoder, value)
            is PulseReplayIncrementalMutationData -> PulseReplayIncrementalMutationData.serializer().serialize(encoder, value)
            is PulseReplayMouseInteractionData -> PulseReplayMouseInteractionData.serializer().serialize(encoder, value)
            is PulseReplayCustomEventData -> PulseReplayCustomEventData.serializer().serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): PulseReplayEventData {
        throw UnsupportedOperationException("Deserialization of PulseReplayEventData is not supported")
    }
}

@Serializable
public data class PulseReplayMetaData(
    public val href: String,
    public val width: Int,
    public val height: Int,
) : PulseReplayEventData()

@Serializable
public data class PulseReplayFullSnapshotData(
    public val wireframes: List<PulseReplayWireframe>,
    public val initialOffset: PulseReplayOffset,
) : PulseReplayEventData()

@Serializable
public data class PulseReplayOffset(
    public val top: Int,
    public val left: Int,
)

@Serializable
public data class PulseReplayIncrementalMutationData(
    public val source: Int,
    public val adds: List<PulseReplayMutatedNode>? = null,
    public val removes: List<PulseReplayRemovedNode>? = null,
    public val updates: List<PulseReplayMutatedNode>? = null,
) : PulseReplayEventData()

@Serializable
public data class PulseReplayMutatedNode(
    public val parentId: Int?,
    public val wireframe: PulseReplayWireframe,
)

@Serializable
public data class PulseReplayRemovedNode(
    public val id: Int,
    public val parentId: Int? = null,
)

@Serializable
public data class PulseReplayMouseInteractionData(
    public val id: Int,
    public val type: Int,
    public val x: Int,
    public val y: Int,
    public val source: Int,
    public val pointerType: Int,
    public val positions: List<PulseReplayMousePosition>? = null,
) : PulseReplayEventData()

@Serializable
public data class PulseReplayMousePosition(
    public val x: Int,
    public val y: Int,
    public val id: Int,
    public val timeOffset: Long? = null,
)

@Serializable
public data class PulseReplayCustomEventData(
    public val tag: String,
    public val payload: JsonObject,
) : PulseReplayEventData()

@Serializable
public data class PulseReplayWireframe(
    public val id: Int,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
    public val type: String? = null,
    public val text: String? = null,
    public val base64: String? = null,
    public val inputType: String? = null,
    public val value: JsonElement? = null,
    @SerialName("disabled") public val isDisabled: Boolean? = null,
    @SerialName("checked") public val isChecked: Boolean? = null,
    public val label: String? = null,
    public val parentId: Int? = null,
    public val max: Int? = null,
    public val style: PulseReplayStyle? = null,
    public val childWireframes: List<PulseReplayWireframe>? = null,
    public val options: List<String>? = null,
)

@Serializable
public data class PulseReplayStyle(
    public val color: String? = null,
    public val backgroundColor: String? = null,
    public val backgroundImage: String? = null,
    public val borderWidth: Int? = null,
    public val borderRadius: Int? = null,
    public val borderColor: String? = null,
    public val fontSize: Int? = null,
    public val fontFamily: String? = null,
    public val horizontalAlign: String? = null,
    public val verticalAlign: String? = null,
    public val paddingTop: Int? = null,
    public val paddingBottom: Int? = null,
    public val paddingLeft: Int? = null,
    public val paddingRight: Int? = null,
    public val bar: String? = null,
    public val iconLeft: String? = null,
    public val iconRight: String? = null,
)

@Serializable
public data class PulseReplayEnvelope(
    public val event: String,
    @SerialName("project_id") public val projectId: String,
    @SerialName("user_id") public val userId: String,
    public val properties: PulseReplayEnvelopeProperties,
)

@Serializable
public data class PulseReplayEnvelopeProperties(
    @SerialName("session_id") public val sessionId: String,
    @SerialName("snapshot_data") public val snapshotData: List<PulseReplaySnapshotEvent>,
    @SerialName("snapshot_source") public val snapshotSource: String,
)
