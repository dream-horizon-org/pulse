package com.pulse.otel.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PulseOtelUtils
 */
class PulseOtelUtilsTest {

    @Test
    fun `normaliseUrl removes query parameters`() {
        val urlWithQuery = "https://api.example.com/users?page=1&limit=10"
        val expected = "https://api.example.com/users"
        val result = PulseOtelUtils.normaliseUrl(urlWithQuery)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles URL without query parameters`() {
        val urlWithoutQuery = "https://api.example.com/users"
        val result = PulseOtelUtils.normaliseUrl(urlWithoutQuery)
        assertEquals(urlWithoutQuery, result)
    }

    @Test
    fun `normaliseUrl handles URL with only question mark`() {
        val urlWithOnlyQuestionMark = "https://api.example.com/users?"
        val expected = "https://api.example.com/users"
        val result = PulseOtelUtils.normaliseUrl(urlWithOnlyQuestionMark)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles complex query parameters`() {
        val urlWithComplexQuery = "https://api.example.com/search?q=test&category=electronics&sort=price&order=asc"
        val expected = "https://api.example.com/search"
        val result = PulseOtelUtils.normaliseUrl(urlWithComplexQuery)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles URL with fragment`() {
        // Note: substringBefore("?") will preserve fragments if they come before query params
        // This test verifies current behavior
        val urlWithFragment = "https://api.example.com/users#section?page=1"
        val expected = "https://api.example.com/users#section"
        val result = PulseOtelUtils.normaliseUrl(urlWithFragment)
        assertEquals(expected, result)
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

    // UUID replacement tests
    @Test
    fun `normaliseUrl replaces standard UUID format in path`() {
        val urlWithUuid = "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/profile"
        val expected = "https://api.example.com/users/{uuid}/profile"
        val result = PulseOtelUtils.normaliseUrl(urlWithUuid)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl replaces UUID without hyphens in path`() {
        val urlWithUuidNoHyphens = "https://api.example.com/users/550e8400e29b41d4a716446655440000/profile"
        val expected = "https://api.example.com/users/{uuid}/profile"
        val result = PulseOtelUtils.normaliseUrl(urlWithUuidNoHyphens)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl replaces multiple UUIDs in path`() {
        val urlWithMultipleUuids = "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/posts/123e4567-e89b-12d3-a456-426614174000"
        val expected = "https://api.example.com/users/{uuid}/posts/{uuid}"
        val result = PulseOtelUtils.normaliseUrl(urlWithMultipleUuids)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles UUID with query parameters`() {
        val urlWithUuidAndQuery = "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000?include=profile&fields=name"
        val expected = "https://api.example.com/users/{uuid}"
        val result = PulseOtelUtils.normaliseUrl(urlWithUuidAndQuery)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles UUID at end of path`() {
        val urlWithUuidAtEnd = "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000"
        val expected = "https://api.example.com/users/{uuid}"
        val result = PulseOtelUtils.normaliseUrl(urlWithUuidAtEnd)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles UUID at start of path`() {
        val urlWithUuidAtStart = "https://api.example.com/550e8400-e29b-41d4-a716-446655440000/users"
        val expected = "https://api.example.com/{uuid}/users"
        val result = PulseOtelUtils.normaliseUrl(urlWithUuidAtStart)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles UUID in middle of path`() {
        val urlWithUuidInMiddle = "https://api.example.com/api/v1/users/550e8400-e29b-41d4-a716-446655440000/profile/settings"
        val expected = "https://api.example.com/api/v1/users/{uuid}/profile/settings"
        val result = PulseOtelUtils.normaliseUrl(urlWithUuidInMiddle)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl does not replace non-UUID hex strings`() {
        // Short hex strings should not be replaced
        val urlWithShortHex = "https://api.example.com/users/abc123/profile"
        val result = PulseOtelUtils.normaliseUrl(urlWithShortHex)
        assertEquals(urlWithShortHex, result)
    }

    @Test
    fun `normaliseUrl handles URL with both UUID and query parameters`() {
        val urlWithBoth = "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000?page=1&limit=10"
        val expected = "https://api.example.com/users/{uuid}"
        val result = PulseOtelUtils.normaliseUrl(urlWithBoth)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles UUID with uppercase letters`() {
        val urlWithUppercaseUuid = "https://api.example.com/users/550E8400-E29B-41D4-A716-446655440000/profile"
        val expected = "https://api.example.com/users/{uuid}/profile"
        val result = PulseOtelUtils.normaliseUrl(urlWithUppercaseUuid)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles UUID with mixed case`() {
        val urlWithMixedCaseUuid = "https://api.example.com/users/550e8400-E29B-41d4-A716-446655440000/profile"
        val expected = "https://api.example.com/users/{uuid}/profile"
        val result = PulseOtelUtils.normaliseUrl(urlWithMixedCaseUuid)
        assertEquals(expected, result)
    }

    @Test
    fun `normaliseUrl handles URL without UUID`() {
        val urlWithoutUuid = "https://api.example.com/users/123/profile"
        val result = PulseOtelUtils.normaliseUrl(urlWithoutUuid)
        assertEquals(urlWithoutUuid, result)
    }

    @Test
    fun `normaliseUrl handles complex URL with UUID and query`() {
        val complexUrl = "https://api.example.com/api/v1/users/550e8400-e29b-41d4-a716-446655440000/posts/123e4567-e89b-12d3-a456-426614174000?page=1&limit=10&sort=date"
        val expected = "https://api.example.com/api/v1/users/{uuid}/posts/{uuid}"
        val result = PulseOtelUtils.normaliseUrl(complexUrl)
        assertEquals(expected, result)
    }
}

