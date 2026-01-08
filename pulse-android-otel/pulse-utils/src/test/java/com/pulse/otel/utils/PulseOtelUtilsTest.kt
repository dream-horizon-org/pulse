package com.pulse.otel.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PulseOtelUtilsTest {
    @ParameterizedTest(name = "normaliseUrl: {2} - from {0} to {1}")
    @MethodSource("getUrlNormalizationTestCases")
    fun `normaliseUrl test cases`(
        inputUrl: String,
        expectedOutput: String,
        description: String,
    ) {
        val result = PulseOtelUtils.normaliseUrl(inputUrl)
        assertThat(result).describedAs("Failed for: $description").isEqualTo(expectedOutput)
    }

    @ParameterizedTest(name = "isNetworkSpan returns true for span with http method {0}")
    @MethodSource("getHttpMethodTestCases")
    fun `isNetworkSpan returns true for span with http method attribute`(httpMethod: String) {
        val tracer = SdkTracerProvider.builder().build().get("test")

        @Suppress("DEPRECATION")
        val span =
            tracer
                .spanBuilder("test-span")
                .setAttribute(HttpIncubatingAttributes.HTTP_METHOD, httpMethod)
                .startSpan()

        assertIsNetworkSpan(span, true)
    }

    @Test
    fun `isNetworkSpan returns false for span without http method attribute`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span =
            tracer
                .spanBuilder("test-span")
                .setAttribute(
                    AttributeKey
                        .stringKey("other.attribute"),
                    "value",
                ).startSpan()

        assertIsNetworkSpan(span, false)
    }

    @Test
    fun `isNetworkSpan returns false for span with no attributes`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span = tracer.spanBuilder("test-span").startSpan()

        assertIsNetworkSpan(span, false)
    }

    @Test
    fun `isNetworkSpan returns false for span with multiple attributes but no http method`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span =
            tracer
                .spanBuilder("test-span")
                .setAttribute(
                    AttributeKey
                        .stringKey("span.kind"),
                    "server",
                ).setAttribute(
                    AttributeKey
                        .stringKey("service.name"),
                    "test-service",
                ).setAttribute(
                    AttributeKey
                        .longKey("duration"),
                    100L,
                ).startSpan()

        assertIsNetworkSpan(span, false)
    }

    @Test
    fun `putAttributesFrom transforms map with different value types to attributes`() {
        val nestedAttributes =
            Attributes
                .builder()
                .put("nested.key1", "nested.value1")
                .put("nested.key2", 42L)
                .build()

        val map =
            mapOf(
                "string.key" to "string.value",
                "long.key" to 123L,
                "double.key" to 45.67,
                "boolean.key" to true,
                "attributes.key" to nestedAttributes,
                "int.key" to 999, // Int will be converted to string via else branch
                "null.key" to null, // null will be converted to null string
            )

        val attributesBuilder = Attributes.builder()
        attributesBuilder putAttributesFrom map
        val attributes = attributesBuilder.build()

        assertThat(attributes.get(AttributeKey.stringKey("string.key"))).isEqualTo("string.value")
        assertThat(attributes.get(AttributeKey.longKey("long.key"))).isEqualTo(123L)
        assertThat(attributes.get(AttributeKey.doubleKey("double.key"))).isEqualTo(45.67)
        assertThat(attributes.get(AttributeKey.booleanKey("boolean.key"))).isEqualTo(true)
        assertThat(attributes.get(AttributeKey.stringKey("nested.key1"))).isEqualTo("nested.value1")
        assertThat(attributes.get(AttributeKey.longKey("nested.key2"))).isEqualTo(42L)
        assertThat(attributes.get(AttributeKey.stringKey("int.key"))).isEqualTo("999")
        assertThat(attributes.get(AttributeKey.stringKey("null.key"))).isNull()
    }

    @Test
    fun `putAttributesFrom excludes attributes with pulse internal prefix`() {
        val map =
            mapOf(
                "pulse.internal.debug" to "also.should.not.appear",
                "pulse.internal" to "also.should.not.appear",
            )

        val attributesBuilder = Attributes.builder()
        attributesBuilder putAttributesFrom map
        val attributes = attributesBuilder.build()

        assertThat(attributes.get(AttributeKey.stringKey("pulse.internal.debug"))).isNull()
        assertThat(attributes.get(AttributeKey.stringKey("pulse.internal"))).isNull()
        assertThat(attributes.size()).isZero
    }

    private fun assertIsNetworkSpan(
        span: Span,
        value: Boolean,
    ) {
        assertThat(PulseOtelUtils.isNetworkSpan(span as? ReadableSpan ?: error("Not a ReadableSpan"))).isEqualTo(value)
    }

    companion object {
        @Suppress("LongMethod")
        @JvmStatic
        fun getUrlNormalizationTestCases(): List<Arguments> =
            listOf(
                Arguments.of(
                    "https://api.example.com/users",
                    "https://api.example.com/users",
                    "handles URL without query parameters",
                ),
                Arguments.of(
                    "https://api.example.com/users?",
                    "https://api.example.com/users",
                    "handles URL with only question mark",
                ),
                Arguments.of(
                    "https://api.example.com/search?q=test&category=electronics&sort=price&order=asc",
                    "https://api.example.com/search",
                    "handles complex query parameters",
                ),
                Arguments.of(
                    "https://api.example.com?param=value",
                    "https://api.example.com",
                    "removes query from root path",
                ),
                Arguments.of(
                    "https://api.example.com/users#section?page=1",
                    "https://api.example.com/users#section",
                    "handles URL with fragment",
                ),
                Arguments.of(
                    "https://api.example.com/users#section",
                    "https://api.example.com/users#section",
                    "handles URL with fragment only",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/profile",
                    "https://api.example.com/users/[redacted]/profile",
                    "replaces standard UUID format",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400e29b41d4a716446655440000/profile",
                    "https://api.example.com/users/[redacted]/profile",
                    "replaces UUID without hyphens",
                ),
                Arguments.of(
                    "https://api.example.com/550e8400-e29b-41d4-a716-446655440000/users",
                    "https://api.example.com/[redacted]/users",
                    "replaces UUID at path start",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000",
                    "https://api.example.com/users/[redacted]",
                    "replaces UUID at path end",
                ),
                Arguments.of(
                    "https://api.example.com/users/550E8400-E29B-41D4-A716-446655440000/profile",
                    "https://api.example.com/users/[redacted]/profile",
                    "replaces uppercase UUID",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-E29B-41d4-A716-446655440000/profile",
                    "https://api.example.com/users/[redacted]/profile",
                    "replaces mixed case UUID",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/posts/123e4567-e89b-12d3-a456-426614174000",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces multiple UUIDs",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000?page=1",
                    "https://api.example.com/users/[redacted]",
                    "replaces UUID with query params",
                ),
                Arguments.of(
                    "https://api.example.com/users/12345",
                    "https://api.example.com/users/[redacted]",
                    "replaces numeric IDs (3+ digits)",
                ),
                Arguments.of(
                    "https://api.example.com/users/123/posts/987654",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces multiple numeric IDs",
                ),
                Arguments.of(
                    "https://api.example.com/users/12",
                    "https://api.example.com/users/12",
                    "does not replace 2-digit numeric",
                ),
                Arguments.of(
                    "https://api.example.com:8080/users/12345",
                    "https://api.example.com:8080/users/[redacted]",
                    "replaces numeric ID with port number",
                ),
                Arguments.of(
                    "https://api.example.com/users/507f1f77bcf86cd799439011",
                    "https://api.example.com/users/[redacted]",
                    "replaces MongoDB ObjectId",
                ),
                Arguments.of(
                    "https://api.example.com/users/507F1F77BCF86CD799439011",
                    "https://api.example.com/users/[redacted]",
                    "replaces uppercase MongoDB ObjectId",
                ),
                Arguments.of(
                    "https://api.example.com/users/507f1f77bcf86cd799439011/posts",
                    "https://api.example.com/users/[redacted]/posts",
                    "replaces MongoDB ObjectId in middle",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV3",
                    "https://api.example.com/users/[redacted]",
                    "replaces ULID (26 chars)",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV3/profile",
                    "https://api.example.com/users/[redacted]/profile",
                    "replaces ULID in middle",
                ),
                Arguments.of(
                    "https://api.example.com/01AN4Z07BY79KA1307SR9X4MV3/users",
                    "https://api.example.com/[redacted]/users",
                    "replaces ULID at path start",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV3?page=1",
                    "https://api.example.com/users/[redacted]",
                    "replaces ULID with query params",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV3/posts/01BN4Z07BY79KA1307SR9X4MV4",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces multiple ULIDs",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV3/posts/12345",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces ULID and numeric ID",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV3/posts/550e8400-e29b-41d4-a716-446655440000",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces ULID and UUID",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV",
                    "https://api.example.com/users/[redacted]",
                    "replaces 25-char alphanumeric string (matches generic pattern)",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MV34",
                    "https://api.example.com/users/[redacted]",
                    "replaces 27-char alphanumeric string (matches generic pattern)",
                ),
                Arguments.of(
                    "https://api.example.com/users/01AN4Z07BY79KA1307SR9X4MVI",
                    "https://api.example.com/users/[redacted]",
                    "replaces string with invalid ULID character (I) - matches alphanumeric pattern",
                ),
                Arguments.of(
                    "https://api.example.com/commits/abc123def456789012345678901234567890abcd",
                    "https://api.example.com/commits/[redacted]",
                    "replaces 40-char git commit hash",
                ),
                Arguments.of(
                    "https://api.example.com/commits/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "https://api.example.com/commits/[redacted]",
                    "replaces 64-char git commit hash",
                ),
                Arguments.of(
                    "https://api.example.com/commits/ABC123DEF456789012345678901234567890ABCD",
                    "https://api.example.com/commits/[redacted]",
                    "replaces uppercase git hash",
                ),
                Arguments.of(
                    "https://api.example.com/users/abc123def456ghi789",
                    "https://api.example.com/users/[redacted]",
                    "replaces 18-char alphanumeric string",
                ),
                Arguments.of(
                    "https://api.example.com/users/abc123def456ghi789jkl012mno345",
                    "https://api.example.com/users/[redacted]",
                    "replaces 30-char alphanumeric string",
                ),
                Arguments.of(
                    "https://api.example.com/users/ABC123DEF456GHI789",
                    "https://api.example.com/users/[redacted]",
                    "replaces uppercase alphanumeric string",
                ),
                Arguments.of(
                    "https://api.example.com/users/abc123def456ghi789/posts",
                    "https://api.example.com/users/[redacted]/posts",
                    "replaces alphanumeric string in middle",
                ),
                Arguments.of(
                    "https://api.example.com/users/abc123def456ghi789?page=1",
                    "https://api.example.com/users/[redacted]",
                    "replaces alphanumeric string with query",
                ),
                Arguments.of(
                    "https://api.example.com/users/abc123def456ghi",
                    "https://api.example.com/users/abc123def456ghi",
                    "does not replace 15-char alphanumeric",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/posts/12345?page=1",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces UUID and numeric ID",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/posts/507f1f77bcf86cd799439011",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces UUID and MongoDB ObjectId",
                ),
                Arguments.of(
                    "https://api.example.com/users/12345/posts/abc123def456ghi789",
                    "https://api.example.com/users/[redacted]/posts/[redacted]",
                    "replaces numeric ID and alphanumeric",
                ),
                Arguments.of(
                    "https://api.example.com/users/550e8400-e29b-41d4-a716-446655440000/posts/12345/comments/507f1f77bcf86cd799439011",
                    "https://api.example.com/users/[redacted]/posts/[redacted]/comments/[redacted]",
                    "replaces multiple different ID types",
                ),
                Arguments.of(
                    "https://api.example.com/550e8400-e29b-41d4-a716-446655440000/?page=1&id=550e8400-e29b-41d4-a716-446655440000",
                    "https://api.example.com/[redacted]/",
                    "query params removed before normalization",
                ),
                Arguments.of(
                    "http://api.example.com/users/12345",
                    "http://api.example.com/users/[redacted]",
                    "handles HTTP protocol",
                ),
                Arguments.of(
                    "https://subdomain.api.example.com/users/12345",
                    "https://subdomain.api.example.com/users/[redacted]",
                    "handles subdomain",
                ),
                Arguments.of(
                    "https://api.example.com/api/v1/users/12345",
                    "https://api.example.com/api/v1/users/[redacted]",
                    "handles API version in path",
                ),
                Arguments.of(
                    "",
                    "",
                    "handles empty string",
                ),
                Arguments.of(
                    "https://api.example.com",
                    "https://api.example.com",
                    "handles root URL",
                ),
            )

        @JvmStatic
        fun getHttpMethodTestCases(): List<Arguments> =
            listOf(
                Arguments.of("GET"),
                Arguments.of("POST"),
                Arguments.of(""),
            )
    }
}
