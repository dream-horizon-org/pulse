package com.pulse.android.sdk.replay.encoding

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName

internal object ReplayGson {
    val instance: Gson =
        GsonBuilder()
            .registerTypeAdapter(
                TransportEventData::class.java,
                JsonSerializer<TransportEventData> { src, _, context ->
                    context.serialize(src, src::class.java)
                },
            ).create()
}

internal data class TransportReplayEvent(
    val type: Int,
    val timestamp: Long,
    val data: TransportEventData? = null,
)

internal sealed class TransportEventData

internal data class TransportMetaData(
    val href: String,
    val width: Int,
    val height: Int,
) : TransportEventData()

internal data class TransportFullSnapshotData(
    val wireframes: List<TransportWireframe>,
    val initialOffset: TransportOffset,
) : TransportEventData()

internal data class TransportOffset(
    val top: Int,
    val left: Int,
)

internal data class TransportIncrementalMutationData(
    val source: Int,
    val adds: List<TransportMutatedNode>? = null,
    val removes: List<TransportRemovedNode>? = null,
    val updates: List<TransportMutatedNode>? = null,
) : TransportEventData()

internal data class TransportMutatedNode(
    val parentId: Int?,
    val wireframe: TransportWireframe,
)

internal data class TransportRemovedNode(
    val id: Int,
    val parentId: Int? = null,
)

internal data class TransportMouseInteractionData(
    val id: Int,
    val type: Int,
    val x: Int,
    val y: Int,
    val source: Int,
    val pointerType: Int,
    val positions: List<TransportMousePosition>? = null,
) : TransportEventData()

internal data class TransportMousePosition(
    val x: Int,
    val y: Int,
    val id: Int,
    val timeOffset: Long? = null,
)

internal data class TransportCustomEventData(
    val tag: String,
    val payload: Map<String, Any>,
) : TransportEventData()

internal data class TransportWireframe(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val type: String? = null,
    val text: String? = null,
    val base64: String? = null,
    val inputType: String? = null,
    val value: Any? = null,
    @SerializedName("disabled") val isDisabled: Boolean? = null,
    @SerializedName("checked") val isChecked: Boolean? = null,
    val label: String? = null,
    val parentId: Int? = null,
    val max: Int? = null,
    val style: TransportStyle? = null,
    val childWireframes: List<TransportWireframe>? = null,
    val options: List<String>? = null,
)

internal data class TransportStyle(
    val color: String? = null,
    val backgroundColor: String? = null,
    val backgroundImage: String? = null,
    val borderWidth: Int? = null,
    val borderRadius: Int? = null,
    val borderColor: String? = null,
    val fontSize: Int? = null,
    val fontFamily: String? = null,
    val horizontalAlign: String? = null,
    val verticalAlign: String? = null,
    val paddingTop: Int? = null,
    val paddingBottom: Int? = null,
    val paddingLeft: Int? = null,
    val paddingRight: Int? = null,
    val bar: String? = null,
    val iconLeft: String? = null,
    val iconRight: String? = null,
)

internal data class TransportEnvelope(
    val event: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("user_id") val userId: String,
    val properties: TransportEnvelopeProperties,
)

internal data class TransportEnvelopeProperties(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("snapshot_data") val snapshotData: List<TransportReplayEvent>,
    @SerializedName("snapshot_source") val snapshotSource: String,
)
