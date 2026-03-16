package com.pulse.android.sdk.replay.encoding

import com.pulse.android.sdk.replay.events.ReplayCustomEventData
import com.pulse.android.sdk.replay.events.ReplayEventData
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayFullSnapshotData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.android.sdk.replay.events.ReplayMetaData
import com.pulse.android.sdk.replay.events.ReplayMousePosition
import com.pulse.android.sdk.replay.events.ReplayMutatedNode
import com.pulse.android.sdk.replay.events.ReplayRemovedNode
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes a batch of [ReplayEvent] to a JSON array string for transport.
 * Event type is encoded as integer (schema/PostHog compatible). Each element has "type", "timestamp", "data".
 */
internal object ReplayEventPayloadEncoder {

    fun encodeToJson(events: List<ReplayEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            arr.put(
                JSONObject().apply {
                    put("type", e.type.value)
                    put("timestamp", e.timestamp)
                    put("data", encodeEventData(e.data))
                },
            )
        }
        return arr.toString()
    }

    private fun encodeEventData(data: ReplayEventData?): Any {
        if (data == null) return JSONObject.NULL
        return when (data) {
            is ReplayMetaData -> JSONObject().apply {
                put("href", data.href)
                put("width", data.width)
                put("height", data.height)
            }
            is ReplayFullSnapshotData -> JSONObject().apply {
                put("wireframes", JSONArray().apply {
                    data.wireframes.forEach { put(wireframeToJson(it)) }
                })
                put("initialOffset", JSONObject().apply {
                    put("top", data.initialOffsetTop)
                    put("left", data.initialOffsetLeft)
                })
            }
            is ReplayIncrementalMutationData -> incrementalMutationDataToJson(data)
            is ReplayIncrementalMouseInteractionData -> mouseInteractionDataToJson(data)
            is ReplayCustomEventData -> JSONObject().apply {
                put("tag", data.tag)
                put("payload", mapToJson(data.payload))
            }
        }
    }

    private fun incrementalMutationDataToJson(d: ReplayIncrementalMutationData): JSONObject {
        return JSONObject().apply {
            put("source", d.source.value)
            d.adds?.let { list -> put("adds", JSONArray().apply { list.forEach { put(mutatedNodeToJson(it)) } }) }
            d.removes?.let { list -> put("removes", JSONArray().apply { list.forEach { put(removedNodeToJson(it)) } }) }
            d.updates?.let { list -> put("updates", JSONArray().apply { list.forEach { put(mutatedNodeToJson(it)) } }) }
        }
    }

    private fun mutatedNodeToJson(n: ReplayMutatedNode): JSONObject {
        return JSONObject().apply {
            put("parentId", n.parentId ?: JSONObject.NULL)
            put("wireframe", wireframeToJson(n.wireframe))
        }
    }

    private fun removedNodeToJson(n: ReplayRemovedNode): JSONObject {
        return JSONObject().apply {
            put("id", n.id)
            n.parentId?.let { put("parentId", it) }
        }
    }

    private fun mouseInteractionDataToJson(d: ReplayIncrementalMouseInteractionData): JSONObject {
        return JSONObject().apply {
            put("id", d.id)
            put("type", d.type.value)
            put("x", d.x)
            put("y", d.y)
            put("source", d.source.value)
            put("pointerType", d.pointerType)
            d.positions?.let { list -> put("positions", JSONArray().apply { list.forEach { put(mousePositionToJson(it)) } }) }
        }
    }

    private fun mousePositionToJson(p: ReplayMousePosition): JSONObject {
        return JSONObject().apply {
            put("x", p.x)
            put("y", p.y)
            put("id", p.id)
            p.timeOffset?.let { put("timeOffset", it) }
        }
    }

    private fun wireframeToJson(w: ReplayWireframe): JSONObject {
        return JSONObject().apply {
            put("id", w.id)
            put("x", w.x)
            put("y", w.y)
            put("width", w.width)
            put("height", w.height)
            w.type?.let { put("type", it) }
            w.text?.let { put("text", it) }
            w.base64?.let { put("base64", it) }
            w.inputType?.let { put("inputType", it) }
            w.value?.let { put("value", it) }
            w.disabled?.let { put("disabled", it) }
            w.checked?.let { put("checked", it) }
            w.label?.let { put("label", it) }
            w.parentId?.let { put("parentId", it) }
            w.max?.let { put("max", it) }
            w.style?.let { put("style", styleToJson(it)) }
            w.childWireframes?.let { list ->
                put("childWireframes", JSONArray().apply { list.forEach { put(wireframeToJson(it)) } })
            }
            w.options?.let { put("options", JSONArray(it)) }
        }
    }

    private fun styleToJson(s: ReplayStyle): JSONObject {
        return JSONObject().apply {
            s.color?.let { put("color", it) }
            s.backgroundColor?.let { put("backgroundColor", it) }
            s.backgroundImage?.let { put("backgroundImage", it) }
            s.borderWidth?.let { put("borderWidth", it) }
            s.borderColor?.let { put("borderColor", it) }
            s.borderRadius?.let { put("borderRadius", it) }
            s.fontSize?.let { put("fontSize", it) }
            s.fontFamily?.let { put("fontFamily", it) }
            s.horizontalAlign?.let { put("horizontalAlign", it) }
            s.verticalAlign?.let { put("verticalAlign", it) }
            s.paddingTop?.let { put("paddingTop", it) }
            s.paddingBottom?.let { put("paddingBottom", it) }
            s.paddingLeft?.let { put("paddingLeft", it) }
            s.paddingRight?.let { put("paddingRight", it) }
            s.bar?.let { put("bar", it) }
            s.iconLeft?.let { put("iconLeft", it) }
            s.iconRight?.let { put("iconRight", it) }
        }
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        return JSONObject().apply {
            for ((k, v) in map) put(k, toJsonPrimitive(v))
        }
    }

    private fun toJsonPrimitive(value: Any): Any {
        return when (value) {
            is Number, is Boolean, is String -> value
            is Map<*, *> -> JSONObject().apply {
                for ((k, v) in value) if (k != null && v != null) put(k.toString(), toJsonPrimitive(v))
            }
            is List<*> -> JSONArray().apply {
                for (item in value) if (item != null) put(toJsonPrimitive(item))
            }
            else -> value.toString()
        }
    }
}
