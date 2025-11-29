package com.pulse.otel.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PulseOtelUtilsTest {

    companion object {
        @JvmStatic
        fun urlNormalizationTestCases(): List<Arguments> = listOf(
            Arguments.of(
                "https://api.example.com/users?page=1&limit=10",
                "https://api.example.com/users",
                "removes query parameters"
            ),
            Arguments.of(
                "https://api.example.com/users",
                "https://api.example.com/users",
                "handles URL without query parameters"
            ),
            Arguments.of(
                "https://api.example.com/users?",
                "https://api.example.com/users",
                "handles URL with only question mark"
            ),
            Arguments.of(
                "https://api.example.com/search?q=test&category=electronics&sort=price&order=asc",
                "https://api.example.com/search",
                "handles complex query parameters"
            ),
            Arguments.of(
                "https://api.example.com/users#section?page=1",
                "https://api.example.com/users#section",
                "handles URL with fragment"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/profile",
                "https://api.example.com/users/[uuid]/profile",
                "replaces standard UUID format in path"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000",
                "https://api.example.com/users/[uuid]",
                "handles UUID at end of path"
            ),
            Arguments.of(
                "https://api.example.com/550e8400-e29b-41d4-a716-446655440000/users",
                "https://api.example.com/[uuid]/users",
                "handles UUID at start of path"
            ),
            Arguments.of(
                "https://api.example.com/api/v1/users/550e8400-e29b-41d4-a716-446655440000/profile/settings",
                "https://api.example.com/api/v1/users/[uuid]/profile/settings",
                "handles UUID in middle of path"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400e29b41d4a716446655440000/profile",
                "https://api.example.com/users/[uuid]/profile",
                "replaces UUID without hyphens in path"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/posts/123e4567-e89b-12d3-a456-426614174000",
                "https://api.example.com/users/[uuid]/posts/[uuid]",
                "replaces multiple UUIDs in path"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000?include=profile&fields=name",
                "https://api.example.com/users/[uuid]",
                "handles UUID with query parameters"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000?page=1&limit=10",
                "https://api.example.com/users/[uuid]",
                "handles URL with both UUID and query parameters"
            ),
            Arguments.of(
                "https://api.example.com/users/550E8400-E29B-41D4-A716-446655440000/profile",
                "https://api.example.com/users/[uuid]/profile",
                "handles UUID with uppercase letters"
            ),
            Arguments.of(
                "https://api.example.com/users/550e8400-E29B-41d4-A716-446655440000/profile",
                "https://api.example.com/users/[uuid]/profile",
                "handles UUID with mixed case"
            ),
            Arguments.of(
                "https://api.example.com/api/v1/users/550e8400-e29b-41d4-a716-446655440000/posts/123e4567-e89b-12d3-a456-426614174000?page=1&limit=10&sort=date",
                "https://api.example.com/api/v1/users/[uuid]/posts/[uuid]",
                "handles complex URL with UUID and query"
            ),
            Arguments.of(
                "https://api.example.com/users/abc123/profile",
                "https://api.example.com/users/abc123/profile",
                "does not replace non-UUID hex strings"
            ),
            Arguments.of(
                "https://api.example.com/users/123/profile",
                "https://api.example.com/users/123/profile",
                "handles URL without UUID"
            )
        )
    }

    @ParameterizedTest(name = "normaliseUrl: {2} - from {0} to {1}")
    @MethodSource("urlNormalizationTestCases")
    fun `normaliseUrl test cases`(inputUrl: String, expectedOutput: String, description: String) {
        val result = PulseOtelUtils.normaliseUrl(inputUrl)
        assertEquals(expectedOutput, result, "Failed for: $description")
    }

    @Test
    fun `isNetworkSpan returns true for span with http method attribute`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span = tracer.spanBuilder("test-span")
            .setAttribute(AttributeKey.stringKey("http.method"), "GET")
            .startSpan()

        assertTrue(PulseOtelUtils.isNetworkSpan(span as ReadableSpan))
        span.end()
    }

    @Test
    fun `isNetworkSpan returns false for span without http method attribute`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span = tracer.spanBuilder("test-span")
            .setAttribute(AttributeKey.stringKey("other.attribute"), "value")
            .startSpan()

        assertFalse(PulseOtelUtils.isNetworkSpan(span as ReadableSpan))
        span.end()
    }

    @Test
    fun `isNetworkSpan returns false for span with no attributes`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span = tracer.spanBuilder("test-span").startSpan()

        assertFalse(PulseOtelUtils.isNetworkSpan(span as ReadableSpan))
        span.end()
    }
}