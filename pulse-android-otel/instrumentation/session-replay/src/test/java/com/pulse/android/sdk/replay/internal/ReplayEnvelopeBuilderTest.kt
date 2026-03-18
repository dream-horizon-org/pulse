package com.pulse.android.sdk.replay.internal

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReplayEnvelopeBuilderTest {
    @Test
    fun `buildEnvelope produces valid JSON with session_id and snapshot_source`() {
        val json =
            ReplayEnvelopeBuilder.buildEnvelope(
                sessionId = "sid-123",
                events = emptyList(),
                projectId = "proj-1",
                userId = "user-1",
            )
        assertThat(json).contains("snapshot")
        assertThat(json).contains("sid-123")
        assertThat(json).contains("proj-1")
        assertThat(json).contains("user-1")
        assertThat(json).contains("android")
        val obj = JsonParser.parseString(json).asJsonObject
        assertThat(obj.get("event").asJsonPrimitive.asString).isEqualTo("snapshot")
        assertThat(
            obj
                .getAsJsonObject("properties")
                .get("session_id")
                .asJsonPrimitive.asString,
        ).isEqualTo("sid-123")
        assertThat(obj.getAsJsonObject("properties").has("snapshot_data")).isTrue()
    }

    @Test
    fun `buildEnvelope uses anonymous when userId is empty`() {
        val json =
            ReplayEnvelopeBuilder.buildEnvelope(
                sessionId = "s",
                events = emptyList(),
                projectId = "p",
                userId = "",
            )
        assertThat(
            JsonParser
                .parseString(json)
                .asJsonObject
                .get("user_id")
                .asJsonPrimitive.asString,
        ).isEqualTo("anonymous")
    }

    @Test
    fun `getSessionIdsForLog returns session_id for single envelope`() {
        val payload = """{"event":"snapshot","properties":{"session_id":"abc-123"}}"""
        val result = ReplayEnvelopeBuilder.getSessionIdsForLog(payload)
        assertThat(result).isEqualTo("session_id: abc-123")
    }

    @Test
    fun `getSessionIdsForLog returns session_ids for batch`() {
        val payload = """[{"properties":{"session_id":"a"}},{"properties":{"session_id":"b"}}]"""
        val result = ReplayEnvelopeBuilder.getSessionIdsForLog(payload)
        assertThat(result).startsWith("session_ids: ")
        assertThat(result).contains("a").contains("b")
    }

    @Test
    fun `getSessionIdsForLog returns null for empty or invalid payload`() {
        assertThat(ReplayEnvelopeBuilder.getSessionIdsForLog("")).isNull()
        assertThat(ReplayEnvelopeBuilder.getSessionIdsForLog("{}")).isNull()
        assertThat(ReplayEnvelopeBuilder.getSessionIdsForLog("not json")).isNull()
    }

    @Test
    fun `getEventTypesSummary returns summary for payload with snapshot_data`() {
        val payload = """{"properties":{"snapshot_data":[{"type":4,"timestamp":0,"data":{}}]}}"""
        val result = ReplayEnvelopeBuilder.getEventTypesSummary(payload)
        assertThat(result).isNotNull
        assertThat(result).contains("META")
        assertThat(result).contains("(1)")
    }

    @Test
    fun `getEventTypesSummary returns null for empty or invalid payload`() {
        assertThat(ReplayEnvelopeBuilder.getEventTypesSummary("")).isNull()
        assertThat(ReplayEnvelopeBuilder.getEventTypesSummary("{}")).isNull()
    }
}
