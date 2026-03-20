package com.pulse.android.sdk.replay.internal

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.pulse.android.sdk.replay.encoding.ReplayEventPayloadEncoder
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayEventType
import com.pulse.android.sdk.replay.models.PulseReplayEnvelope
import com.pulse.android.sdk.replay.models.PulseReplayEnvelopeProperties
import com.pulse.android.sdk.replay.models.PulseReplayGson

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
        return PulseReplayGson.instance.toJson(envelope)
    }

    fun getSessionIdsForLog(payload: String): String? =
        try {
            val envelopes = parseEnvelopes(payload)
            val ids =
                envelopes
                    .mapNotNull { envelope ->
                        envelope
                            .get("properties")
                            ?.takeIf { it.isJsonObject }
                            ?.asJsonObject
                            ?.get("session_id")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asJsonPrimitive
                            ?.asString
                            ?.takeIf { it.isNotEmpty() }
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
            if (typeCounts.isEmpty()) {
                null
            } else {
                typeCounts.entries.joinToString(", ") { "${it.key}(${it.value})" }
            }
        } catch (_: Throwable) {
            null
        }

    private fun countEventTypes(payload: String): Map<String, Int> {
        val envelopes = parseEnvelopes(payload)
        val typeCounts = mutableMapOf<String, Int>()
        for (envelope in envelopes) {
            val props = envelope.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject
            val snapshotData = props?.get("snapshot_data")?.takeIf { it.isJsonArray }?.asJsonArray
            if (snapshotData != null) {
                countEventsInSnapshot(snapshotData, typeCounts)
            }
        }
        return typeCounts
    }

    private fun countEventsInSnapshot(
        snapshotData: com.google.gson.JsonArray,
        typeCounts: MutableMap<String, Int>,
    ) {
        for (element in snapshotData) {
            val item = element.takeIf { it.isJsonObject }?.asJsonObject
            val name = item?.let { resolveEventTypeName(it) }
            if (name != null) {
                typeCounts[name] = (typeCounts[name] ?: 0) + 1
            }
        }
    }

    private fun resolveEventTypeName(item: com.google.gson.JsonObject): String? {
        val typeInt = item.get("type")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
        if (typeInt < 0) return null
        return when (typeInt) {
            3 -> {
                val data = item.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                val source = data?.get("source")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
                if (source == 2) "Touch" else "ViewMutation"
            }
            5 -> {
                val data = item.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                val tag =
                    data
                        ?.get("tag")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.takeIf { it.isNotEmpty() }
                if (tag != null) "Custom($tag)" else "Custom"
            }
            else -> {
                ReplayEventType.fromValue(typeInt)?.name ?: "type_$typeInt"
            }
        }
    }

    private fun parseEnvelopes(payload: String): List<com.google.gson.JsonObject> {
        val trimmed = payload.trimStart()
        val array =
            if (trimmed.startsWith("[")) {
                JsonParser.parseString(payload).asJsonArray
            } else {
                JsonArray().apply { add(JsonParser.parseString(payload).asJsonObject) }
            }
        return (0 until array.size()).mapNotNull { index ->
            array.get(index).takeIf { it.isJsonObject }?.asJsonObject
        }
    }
}
