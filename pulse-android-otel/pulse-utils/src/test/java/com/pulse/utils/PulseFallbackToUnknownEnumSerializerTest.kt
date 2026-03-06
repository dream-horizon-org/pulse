package com.pulse.utils

import junit.framework.TestCase.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.serializer
import org.junit.Ignore
import org.junit.Test

class PulseFallbackToUnknownEnumSerializerTest {
    private class StatusSerializer :
        PulseFallbackToUnknownEnumSerializer<Status>(
            enumClass = Status::class,
            serialName = "Status",
        )

    @Serializable(with = StatusSerializer::class)
    private enum class Status {
        SUCCESS,
        ERROR,

        UNKNOWN,
    }

    private class StatusWithSerialNameSerializer :
        PulseFallbackToUnknownEnumSerializer<StatusWithSerialName>(
            enumClass = StatusWithSerialName::class,
            serialName = "StatusWithSerialName",
        )

    @Serializable(with = StatusWithSerialNameSerializer::class)
    private enum class StatusWithSerialName {
        @SerialName("SUCCESS")
        INTERNAL_SUCCESS,

        @OptIn(ExperimentalSerializationApi::class)
        @SerialName("ERROR")
        @JsonNames("FAILED")
        INTERNAL_ERROR,

        @SerialName("UNKNOWN")
        INTERNAL_UNKNOWN,
    }

    private val json =
        Json {
            ignoreUnknownKeys = false
            useAlternativeNames = true
        }

    @OptIn(ExperimentalSerializationApi::class)
    private val jsonWithCaseInsensitive =
        Json {
            ignoreUnknownKeys = false
            useAlternativeNames = true
            decodeEnumsCaseInsensitive = true
        }

    @Test
    fun `deserialize known enum value`() {
        val input = "\"SUCCESS\""

        val result = json.decodeFromString(serializer<Status>(), input)

        assertEquals(Status.SUCCESS, result)
    }

    @Ignore("case insensitive serialisation strategy is not supported")
    @Test
    fun `deserialize known enum value lowercase with cass insensitive`() {
        val input = "\"success\""

        val result = jsonWithCaseInsensitive.decodeFromString(serializer<Status>(), input)

        assertEquals(Status.SUCCESS, result)
    }

    @Test
    fun `deserialize unknown enum value falls back`() {
        val input = "\"SOMETHING_NEW\""

        val result = json.decodeFromString<Status>(input)

        assertEquals(Status.UNKNOWN, result)
    }

    @Test
    fun `serialize enum values to string`() {
        val resultSuccess = json.encodeToString(Status.SUCCESS)
        assertEquals("\"SUCCESS\"", resultSuccess)

        val resultUnknown = json.encodeToString(Status.UNKNOWN)
        assertEquals("\"UNKNOWN\"", resultUnknown)
    }

    @Test
    fun `serialize enum value to string with serial name`() {
        val result = json.encodeToString(StatusWithSerialName.INTERNAL_SUCCESS)
        assertEquals("\"SUCCESS\"", result)
    }

    @Test
    fun `deserialize known enum value with serial name`() {
        val input = "\"SUCCESS\""

        val result = json.decodeFromString(serializer<StatusWithSerialName>(), input)

        assertEquals(StatusWithSerialName.INTERNAL_SUCCESS, result)
    }

    @Ignore("case insensitive serialisation strategy is not supported")
    @Test
    fun `deserialize known enum value with serial name lowercase with cass insensitive`() {
        val input = "\"success\""

        val result = jsonWithCaseInsensitive.decodeFromString(serializer<StatusWithSerialName>(), input)

        assertEquals(StatusWithSerialName.INTERNAL_SUCCESS, result)
    }

    @Ignore(value = "Alternate names not supported")
    @Test
    fun `deserialize known enum value with alternate name`() {
        val input = "\"FAILED\""

        val result = json.decodeFromString(serializer<StatusWithSerialName>(), input)

        assertEquals(StatusWithSerialName.INTERNAL_ERROR, result)
    }

    @Ignore(value = "Alternate names not supported")
    @Test
    fun `deserialize known enum value with alternate name lowercase with cass insensitive`() {
        val input = "\"failed\""

        val result = jsonWithCaseInsensitive.decodeFromString(serializer<StatusWithSerialName>(), input)

        assertEquals(StatusWithSerialName.INTERNAL_ERROR, result)
    }
}
