package com.pulse.otel.utils

import junit.framework.TestCase.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test

class PulseFallbackToUnknownEnumSerializerTest {
    private class StatusSerializer : PulseFallbackToUnknownEnumSerializer<Status>(Status::class)

    @Serializable(with = StatusSerializer::class)
    private enum class Status {
        SUCCESS,
        ERROR,

        UNKNOWN,
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Test
    fun `deserialize known enum value`() {
        val input = "\"SUCCESS\""

        val result = json.decodeFromString(serializer<Status>(), input)

        assertEquals(Status.SUCCESS, result)
    }

    @Test
    fun `deserialize another known enum value`() {
        val input = "\"ERROR\""

        val result = json.decodeFromString<Status>(input)

        assertEquals(Status.ERROR, result)
    }

    @Test
    fun `deserialize unknown enum value falls back`() {
        val input = "\"SOMETHING_NEW\""

        val result = json.decodeFromString<Status>(input)

        assertEquals(Status.UNKNOWN, result)
    }

    @Test
    fun `serialize enum value to string`() {
        val result = json.encodeToString(Status.SUCCESS)
        assertEquals("\"success\"", result)
    }

    @Test
    fun `serialize fallback enum value`() {
        val result = json.encodeToString(Status.UNKNOWN)
        assertEquals("\"unknown\"", result)
    }
}
