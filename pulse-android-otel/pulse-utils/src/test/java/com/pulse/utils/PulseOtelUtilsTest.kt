package com.pulse.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PulseOtelUtilsTest {
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
    fun `isNetworkSpan returns true when only HTTP_REQUEST_METHOD is present`() {
        val tracer = SdkTracerProvider.builder().build().get("test")
        val span =
            tracer
                .spanBuilder("test-span")
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                .startSpan()

        assertIsNetworkSpan(span, true)
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

        Assertions.assertThat(attributes.get(AttributeKey.stringKey("string.key"))).isEqualTo("string.value")
        Assertions.assertThat(attributes.get(AttributeKey.longKey("long.key"))).isEqualTo(123L)
        Assertions.assertThat(attributes.get(AttributeKey.doubleKey("double.key"))).isEqualTo(45.67)
        Assertions.assertThat(attributes.get(AttributeKey.booleanKey("boolean.key"))).isEqualTo(true)
        Assertions.assertThat(attributes.get(AttributeKey.stringKey("nested.key1"))).isEqualTo("nested.value1")
        Assertions.assertThat(attributes.get(AttributeKey.longKey("nested.key2"))).isEqualTo(42L)
        Assertions.assertThat(attributes.get(AttributeKey.stringKey("int.key"))).isEqualTo("999")
        Assertions.assertThat(attributes.get(AttributeKey.stringKey("null.key"))).isNull()
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

        Assertions.assertThat(attributes.get(AttributeKey.stringKey("pulse.internal.debug"))).isNull()
        Assertions.assertThat(attributes.get(AttributeKey.stringKey("pulse.internal"))).isNull()
        Assertions.assertThat(attributes.size()).isZero
    }

    private fun assertIsNetworkSpan(
        span: Span,
        value: Boolean,
    ) {
        Assertions
            .assertThat(
                PulseOtelUtils.isNetworkSpan(
                    span as? ReadableSpan ?: error("Not a ReadableSpan"),
                ),
            ).isEqualTo(value)
    }

    @RepeatedTest(10)
    fun `matchesFromRegexCache ensures thread safety by providing isolated Matcher per thread`() {
        val threadCount = 100
        val iterationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val sharedPattern = "test\\d+"
        val correctResults = AtomicInteger(0)
        val incorrectResults = AtomicInteger(0)

        repeat(threadCount) { _ ->
            executor.submit {
                repeat(iterationsPerThread) { iteration ->
                    val matchingInput = "test$iteration"
                    val nonMatchingInput = "fail$iteration"

                    val matchResult = matchingInput.matchesFromRegexCache(sharedPattern)
                    val mismatchResult = nonMatchingInput.matchesFromRegexCache(sharedPattern)

                    if (matchResult && !mismatchResult) {
                        correctResults.incrementAndGet()
                    } else {
                        incorrectResults.incrementAndGet()
                    }
                }
                latch.countDown()
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        Assertions.assertThat(incorrectResults.get()).isEqualTo(0)
        Assertions.assertThat(correctResults.get()).isEqualTo(threadCount * iterationsPerThread)
    }

    @RepeatedTest(10)
    fun `matchesFromRegexCache handles concurrent cache population without race conditions`() {
        val threadCount = 50
        val uniquePatterns = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val correctResults = AtomicInteger(0)
        val incorrectResults = AtomicInteger(0)

        repeat(threadCount) { threadId ->
            executor.submit {
                repeat(100) { iteration ->
                    val patternIndex = (threadId + iteration) % uniquePatterns
                    val pattern = "test$patternIndex\\d+"
                    val matchingInput = "test${patternIndex}123"
                    val nonMatchingInput = "fail${patternIndex}123"

                    val matchResult = matchingInput.matchesFromRegexCache(pattern)
                    val nonMatchResult = nonMatchingInput.matchesFromRegexCache(pattern)

                    if (matchResult && !nonMatchResult) {
                        correctResults.incrementAndGet()
                    } else {
                        incorrectResults.incrementAndGet()
                    }
                }
                latch.countDown()
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        Assertions.assertThat(incorrectResults.get()).isEqualTo(0)
        Assertions.assertThat(correctResults.get()).isEqualTo(threadCount * 100)
    }

    companion object {
        @JvmStatic
        fun getHttpMethodTestCases(): List<Arguments> =
            listOf(
                Arguments.of("GET"),
                Arguments.of("POST"),
                Arguments.of(""),
            )
    }
}
