package com.pulse.android.sdk.replay.internal

import com.pulse.android.sdk.replay.encoding.ReplayEventPayloadEncoder
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayEventType
import com.pulse.android.sdk.replay.models.PulseReplayEnvelope
import com.pulse.android.sdk.replay.models.PulseReplayEnvelopeProperties
import com.pulse.android.sdk.replay.models.PulseReplayJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object ReplayEnvelopeBuilder {
    private const val ANONYMOUS_USER_ID = "anonymous"
    private const val SNAPSHOT_SOURCE = "Android"

    fun buildEnvelope(
        sessionId: String,
        events: List<ReplayEvent>,
        projectId: String,
        userId: String,
        appVersion: String? = null,
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
                        appVersion = appVersion?.takeIf { it.isNotEmpty() },
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
                        val props = envelope["properties"] as? JsonObject ?: return@mapNotNull null
                        val el = props["session_id"] as? JsonPrimitive ?: return@mapNotNull null
                        el.content.takeIf { it.isNotEmpty() }
                    }.toSet()
            when (ids.size) {
                0 -> {
                    null
                }
                1 -> {
                    "session_id: ${ids.first()}"
                }
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
                (envelope["properties"] as? JsonObject)?.get("snapshot_data") as? JsonArray
                    ?: continue
            for (element in snapshotData) {
                val item = element as? JsonObject ?: continue
                val name = resolveEventTypeName(item)
                if (name != null) {
                    typeCounts[name] = (typeCounts[name] ?: 0) + 1
                }
            }
        }
        return typeCounts
    }

    private fun resolveEventTypeName(item: JsonObject): String? {
        val typeEl = item["type"] as? JsonPrimitive ?: return null
        val typeInt = typeEl.int
        if (typeInt < 0) return null
        val dataObj = item["data"] as? JsonObject
        return when (typeInt) {
            3 -> {
                val sourceEl = dataObj?.get("source") as? JsonPrimitive
                val source = sourceEl?.int ?: -1
                if (source == 2) {
                    "Touch"
                } else {
                    "ViewMutation"
                }
            }
            5 -> {
                val tagEl = dataObj?.get("tag") as? JsonPrimitive
                val text = tagEl?.content
                val tag = text?.takeIf { it.isNotEmpty() }
                if (tag != null) {
                    "Custom($tag)"
                } else {
                    "Custom"
                }
            }
            else -> {
                ReplayEventType.fromValue(typeInt)?.name ?: "type_$typeInt"
            }
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
