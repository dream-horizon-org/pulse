package com.pulse.android.sdk.replay.models

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName

/** Gson instance for serializing [PulseReplayEnvelope] and nested wire types. */
@Keep
public object PulseReplayGson {
    public val instance: Gson =
        GsonBuilder()
            .registerTypeAdapter(
                PulseReplayEventData::class.java,
                JsonSerializer<PulseReplayEventData> { src, _, context ->
                    context.serialize(src, src::class.java)
                },
            ).create()
}

@Keep
public data class PulseReplaySnapshotEvent(
    public val type: Int,
    public val timestamp: Long,
    public val data: PulseReplayEventData? = null,
)

@Keep
public sealed class PulseReplayEventData

@Keep
public data class PulseReplayMetaData(
    public val href: String,
    public val width: Int,
    public val height: Int,
) : PulseReplayEventData()

@Keep
public data class PulseReplayFullSnapshotData(
    public val wireframes: List<PulseReplayWireframe>,
    public val initialOffset: PulseReplayOffset,
) : PulseReplayEventData()

@Keep
public data class PulseReplayOffset(
    public val top: Int,
    public val left: Int,
)

@Keep
public data class PulseReplayIncrementalMutationData(
    public val source: Int,
    public val adds: List<PulseReplayMutatedNode>? = null,
    public val removes: List<PulseReplayRemovedNode>? = null,
    public val updates: List<PulseReplayMutatedNode>? = null,
) : PulseReplayEventData()

@Keep
public data class PulseReplayMutatedNode(
    public val parentId: Int?,
    public val wireframe: PulseReplayWireframe,
)

@Keep
public data class PulseReplayRemovedNode(
    public val id: Int,
    public val parentId: Int? = null,
)

@Keep
public data class PulseReplayMouseInteractionData(
    public val id: Int,
    public val type: Int,
    public val x: Int,
    public val y: Int,
    public val source: Int,
    public val pointerType: Int,
    public val positions: List<PulseReplayMousePosition>? = null,
) : PulseReplayEventData()

@Keep
public data class PulseReplayMousePosition(
    public val x: Int,
    public val y: Int,
    public val id: Int,
    public val timeOffset: Long? = null,
)

@Keep
public data class PulseReplayCustomEventData(
    public val tag: String,
    public val payload: Map<String, Any>,
) : PulseReplayEventData()

@Keep
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
    public val value: Any? = null,
    @SerializedName("disabled") public val isDisabled: Boolean? = null,
    @SerializedName("checked") public val isChecked: Boolean? = null,
    public val label: String? = null,
    public val parentId: Int? = null,
    public val max: Int? = null,
    public val style: PulseReplayStyle? = null,
    public val childWireframes: List<PulseReplayWireframe>? = null,
    public val options: List<String>? = null,
)

@Keep
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

@Keep
public data class PulseReplayEnvelope(
    public val event: String,
    @SerializedName("project_id") public val projectId: String,
    @SerializedName("user_id") public val userId: String,
    public val properties: PulseReplayEnvelopeProperties,
)

@Keep
public data class PulseReplayEnvelopeProperties(
    @SerializedName("session_id") public val sessionId: String,
    @SerializedName("snapshot_data") public val snapshotData: List<PulseReplaySnapshotEvent>,
    @SerializedName("snapshot_source") public val snapshotSource: String,
)
