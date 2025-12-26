package com.pulse.otel.utils.models

import junit.framework.TestCase.assertEquals
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Test

class PulseApiResponseTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
            prettyPrint = false
            isLenient = false
            allowSpecialFloatingPointValues = true
            useAlternativeNames = true
        }

    @Test
    fun `serialize success response with data`() {
        val response =
            PulseApiResponse(
                data = "OK",
                error = null,
            )

        val result =
            json.encodeToString(
                PulseApiResponse.serializer(String.serializer()),
                response,
            )

        assertEquals("""{"data":"OK"}""", result)
    }

    @Test
    fun `deserialize success response with data`() {
        val input = """{"data":"OK"}"""

        val result =
            json.decodeFromString(
                PulseApiResponse.serializer(String.serializer()),
                input,
            )

        assertEquals("OK", result.data)
        assertEquals(null, result.error)
    }

    @Test
    fun `serialize error response without data`() {
        val response =
            PulseApiResponse<String>(
                data = null,
                error =
                    PulseApiError(
                        code = "404",
                        message = "Not Found",
                    ),
            )

        val result =
            json.encodeToString(
                PulseApiResponse.serializer(String.serializer()),
                response,
            )

        assertEquals(
            """{"error":{"code":"404","message":"Not Found"}}""",
            result,
        )
    }

    @Test
    fun `deserialize error response without data`() {
        val input =
            """{"error":{"code":"404","message":"Not Found"}}"""

        val result =
            json.decodeFromString(
                PulseApiResponse.serializer(String.serializer()),
                input,
            )

        assertEquals(null, result.data)
        assertEquals("404", result.error?.code)
        assertEquals("Not Found", result.error?.message)
    }

    @Test
    fun `serialize response with both data and error`() {
        val response =
            PulseApiResponse(
                data = "PARTIAL",
                error =
                    PulseApiError(
                        code = "206",
                        message = "Partial Content",
                    ),
            )

        val result =
            json.encodeToString(
                PulseApiResponse.serializer(String.serializer()),
                response,
            )

        assertEquals(
            """{"data":"PARTIAL","error":{"code":"206","message":"Partial Content"}}""",
            result,
        )
    }

    @Test
    fun `deserialize response with both data and error`() {
        val input =
            """{"data":"PARTIAL","error":{"code":"206","message":"Partial Content"}}"""

        val result =
            json.decodeFromString(
                PulseApiResponse.serializer(String.serializer()),
                input,
            )

        assertEquals("PARTIAL", result.data)
        assertEquals("206", result.error?.code)
        assertEquals("Partial Content", result.error?.message)
    }
}
