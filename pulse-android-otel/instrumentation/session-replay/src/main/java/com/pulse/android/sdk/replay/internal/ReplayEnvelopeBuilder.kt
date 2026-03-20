package com.pulse.android.sdk.replay.internal

import com.pulse.android.sdk.replay.encoding.ReplayEventPayloadEncoder
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayEventType
import com.pulse.android.sdk.replay.models.PulseReplayEnvelope
import com.pulse.android.sdk.replay.models.PulseReplayEnvelopeProperties
import com.pulse.android.sdk.replay.models.PulseReplayJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object ReplayEnvelopeBuilder {
    private const val ANONYMOUS_USER_ID = "anonymous"
    private const val SNAPSHOT_SOURCE = "android"

    fun buildEnvelope(
        sessionId: String,
        events: List<ReplayEvent>,
        projectId: String,
        userId: String,
    ): String {
        val wireSnapshotEvents = ReplayEventPayloadEncoder.toPulseReplayWireEvents(events)
        val envelope =
            PulseReplayEnvelope(
                event = "snapshot",
                projectId = projectId,
                userId = userId.ifEmpty { ANONYMOUS_USER_ID },
                properties =
                    PulseReplayEnvelopeProperties(
                        sessionId = sessionId,
                        snapshotData = wireSnapshotEvents,
                        snapshotSource = SNAPSHOT_SOURCE,
                    ),
            )
        return PulseReplayJson.instance.encodeToString(envelope)
    }

    fun getSessionIdsForLog(payload: String): String? =
        try {
            val envelopes = parseEnvelopes(payload)
            val ids =
                envelopes
                    .mapNotNull { envelope ->
                        envelope["properties"]
                            ?.takeIf { it is JsonObject }
                            ?.jsonObject
                            ?.get("session_id")
                            ?.jsonPrimitive
                            ?.content
                            ?.takeIf { it.isNotEmpty() }
                    }.toSet()
            when (ids.size) {
                0 -> null
                1 -> "session_id: ${ids.first()}"
                else -> {
                    val preview = ids.take(3).joinToString(", ")
                    val suffix = if (ids.size > 3) " (+${ids.size - 3} more)" else ""
                    "session_ids: $preview$suffix"
                }
            }
        } catch (_: Throwable) {
            null
        }

    fun getEventTypesSummary(payload: String): String? =
        try {
            val typeCounts = countEventTypes(payload)
            if (typeCounts.isEmpty()) null else typeCounts.entries.joinToString(", ") { "${it.key}(${it.value})" }
        } catch (_: Throwable) {
            null
        }

    private fun countEventTypes(payload: String): Map<String, Int> {
        val envelopes = parseEnvelopes(payload)
        val typeCounts = mutableMapOf<String, Int>()
        for (envelope in envelopes) {
            val snapshotData =
                envelope["properties"]
                    ?.takeIf { it is JsonObject }
                    ?.jsonObject
                    ?.get("snapshot_data")
                    ?.takeIf { it is JsonArray }
                    ?.jsonArray
            if (snapshotData != null) {
                for (element in snapshotData) {
                    val item = element.takeIf { it is JsonObject }?.jsonObject ?: continue
                    val name = resolveEventTypeName(item) ?: continue
                    typeCounts[name] = (typeCounts[name] ?: 0) + 1
                }
            }
        }
        return typeCounts
    }

    private fun resolveEventTypeName(item: JsonObject): String? {
        val typeInt = item["type"]?.jsonPrimitive?.int ?: return null
        if (typeInt < 0) return null
        return when (typeInt) {
            3 -> {
                val source = item["data"]?.takeIf { it is JsonObject }?.jsonObject?.get("source")?.jsonPrimitive?.int ?: -1
                if (source == 2) "Touch" else "ViewMutation"
            }
            5 -> {
                val tag =
                    item["data"]
                        ?.takeIf { it is JsonObject }
                        ?.jsonObject
                        ?.get("tag")
                        ?.jsonPrimitive
                        ?.content
                        ?.takeIf { it.isNotEmpty() }
                if (tag != null) "Custom($tag)" else "Custom"
            }
            else -> ReplayEventType.fromValue(typeInt)?.name ?: "type_$typeInt"
        }
    }

    private fun parseEnvelopes(payload: String): List<JsonObject> {
        val trimmed = payload.trimStart()
        val parsed = Json.parseToJsonElement(payload)
        return if (trimmed.startsWith("[")) {
            parsed.jsonArray.mapNotNull { it.takeIf { e -> e is JsonObject }?.jsonObject }
        } else {
            listOfNotNull(parsed.takeIf { it is JsonObject }?.jsonObject)
        }
    }
}
