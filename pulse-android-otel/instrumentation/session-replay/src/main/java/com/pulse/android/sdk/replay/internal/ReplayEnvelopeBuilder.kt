package com.pulse.android.sdk.replay.internal

import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayEventType
import com.pulse.android.sdk.replay.encoding.ReplayEventPayloadEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the snapshot envelope JSON (event, project_id, user_id, properties) and provides
 * optional payload summary for logging. Used internally by SessionReplayInstrumentation.
 */
internal object ReplayEnvelopeBuilder {

    private const val ANONYMOUS_USER_ID = "anonymous"
    private const val LOG_TAG = "PulseSessionReplay"

    fun buildEnvelope(
        sessionId: String,
        events: List<ReplayEvent>,
        projectId: String,
        userId: String,
    ): String {
        val snapshotDataJson = ReplayEventPayloadEncoder.encodeToJson(events)
        val properties = JSONObject().apply {
            put("session_id", sessionId)
            put("snapshot_data", JSONArray(snapshotDataJson))
            put("snapshot_source", "android")
        }
        return JSONObject().apply {
            put("event", "snapshot")
            put("project_id", projectId)
            put("user_id", userId.ifEmpty { ANONYMOUS_USER_ID })
            put("properties", properties)
        }.toString()
    }

    /** Returns a string suitable for logging, e.g. "session_id: abc-123" or "session_ids: a, b" for batches. */
    fun getSessionIdsForLog(payload: String): String? = try {
        val envelopes = if (payload.trimStart().startsWith("[")) JSONArray(payload) else JSONArray().put(JSONObject(payload))
        val ids = mutableSetOf<String>()
        for (i in 0 until envelopes.length()) {
            val envelope = envelopes.optJSONObject(i) ?: continue
            val props = envelope.optJSONObject("properties") ?: continue
            props.optString("session_id", "").takeIf { it.isNotEmpty() }?.let { ids.add(it) }
        }
        when (ids.size) {
            0 -> null
            1 -> "session_id: ${ids.first()}"
            else -> "session_ids: ${ids.take(3).joinToString(", ")}${if (ids.size > 3) " (+${ids.size - 3} more)" else ""}"
        }
    } catch (_: Throwable) {
        null
    }

    fun getEventTypesSummary(payload: String): String? = try {
        val envelopes = if (payload.trimStart().startsWith("[")) JSONArray(payload) else JSONArray().put(JSONObject(payload))
        val typeCounts = mutableMapOf<String, Int>()
        for (i in 0 until envelopes.length()) {
            val envelope = envelopes.optJSONObject(i) ?: continue
            val props = envelope.optJSONObject("properties") ?: continue
            val snapshotData = props.optJSONArray("snapshot_data") ?: continue
            for (j in 0 until snapshotData.length()) {
                val item = snapshotData.optJSONObject(j) ?: continue
                val typeInt = item.optInt("type", -1)
                if (typeInt < 0) continue
                val name = when (typeInt) {
                    3 -> {
                        val data = item.optJSONObject("data")
                        val source = data?.optInt("source", -1)
                        if (source == 2) "Touch" else "ViewMutation"
                    }
                    5 -> {
                        val data = item.optJSONObject("data")
                        val tag = data?.optString("tag", "")?.takeIf { it.isNotEmpty() }
                        if (tag != null) "Custom($tag)" else "Custom"
                    }
                    else -> ReplayEventType.fromValue(typeInt)?.name ?: "type_$typeInt"
                }
                typeCounts[name] = (typeCounts[name] ?: 0) + 1
            }
        }
        if (typeCounts.isEmpty()) null else typeCounts.entries.joinToString(", ") { "${it.key}(${it.value})" }
    } catch (_: Throwable) {
        null
    }
}
