package com.pulse.android.sdk.replay.encoding

import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
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
public object ReplayEventPayloadEncoder {

    /**
     * Encodes [events] to a JSON array string. Each element has "type" (integer), "timestamp", "data".
     */
    @JvmStatic
    public fun encodeToJson(events: List<ReplayEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            arr.put(
                JSONObject().apply {
                    put("type", e.type.value)
                    put("timestamp", e.timestamp)
                    put("data", toJsonValue(e.data))
                },
            )
        }
        return arr.toString()
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((k, v) in value) if (k != null) obj.put(k.toString(), toJsonValue(v))
                obj
            }
            is List<*> -> {
                val arr = JSONArray()
                for (item in value) arr.put(toJsonValue(item))
                arr
            }
            is ReplayWireframe -> wireframeToJson(value)
            is ReplayStyle -> {
                val obj = JSONObject()
                value.color?.let { obj.put("color", it) }
                value.backgroundColor?.let { obj.put("backgroundColor", it) }
                value.backgroundImage?.let { obj.put("backgroundImage", it) }
                value.borderWidth?.let { obj.put("borderWidth", it) }
                value.fontSize?.let { obj.put("fontSize", it) }
                value.fontFamily?.let { obj.put("fontFamily", it) }
                value.horizontalAlign?.let { obj.put("horizontalAlign", it) }
                value.verticalAlign?.let { obj.put("verticalAlign", it) }
                obj
            }
            is ReplayIncrementalMutationData -> incrementalMutationDataToJson(value)
            is ReplayMutatedNode -> mutatedNodeToJson(value)
            is ReplayRemovedNode -> removedNodeToJson(value)
            is ReplayIncrementalMouseInteractionData -> mouseInteractionDataToJson(value)
            is ReplayMousePosition -> mousePositionToJson(value)
            is Number, is Boolean -> value
            else -> value.toString()
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
            put("type", w.type)
            put("text", w.text)
            put("base64", w.base64)
            put("inputType", w.inputType)
            put("value", toJsonValue(w.value))
            put("disabled", w.disabled)
            put("checked", w.checked)
            put("label", w.label)
            put("parentId", w.parentId)
            put("max", w.max)
            w.style?.let { put("style", toJsonValue(it)) }
            w.childWireframes?.let { list ->
                put("childWireframes", JSONArray().apply { list.forEach { put(wireframeToJson(it)) } })
            }
            w.options?.let { put("options", JSONArray(it)) }
        }
    }
}
