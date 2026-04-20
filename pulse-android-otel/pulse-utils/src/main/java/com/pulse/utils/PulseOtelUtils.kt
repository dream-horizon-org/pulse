package com.pulse.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

public object PulseOtelUtils {
    internal const val HEX_CHARS = "[0-9a-fA-F]"
    internal const val DIGITS = "\\d"
    internal const val ALPHANUMERIC = "[A-Za-z0-9]"
    internal const val ULID_CHARS = "[0-9A-HJKMNP-TV-Z]"
    internal const val REDACTED = "[redacted]"

    // todo when https://github.com/open-telemetry/opentelemetry-android/issues/1393 is fixed
    //  use the new not deprecated attributes
    @Suppress("DEPRECATION")
    public fun isNetworkSpan(span: ReadableSpan): Boolean =
        span.attributes.get(HttpIncubatingAttributes.HTTP_METHOD) != null ||
            span.attributes.get(HttpAttributes.HTTP_REQUEST_METHOD) != null

    public fun isDebug(): Boolean = BuildConfig.DEBUG

    /**
     * Sanitizes the instrumentation name as per the SdkMeter.VALID_INSTRUMENT_NAME_PATTERN.
     */
    public fun sanitizeInstrumentationName(
        name: String,
        fallbackChar: Char = '_',
    ): String {
        // Replace every non-supported character with _
        // Supported characters: alphanumeric, _, ., -, /
        val sanitized =
            name
                .map { char ->
                    when {
                        char.isLetterOrDigit() -> char
                        char == '_' || char == '.' || char == '-' || char == '/' -> char
                        else -> fallbackChar
                    }
                }.joinToString("")

        val withLetterStart =
            if (sanitized.isNotEmpty() && sanitized[0].isLetter()) {
                sanitized
            } else {
                "m$sanitized"
            }

        // Ensure it's 255 or fewer characters
        return if (withLetterStart.length <= 255) {
            withLetterStart
        } else {
            withLetterStart.take(255)
        }
    }
}

public infix fun AttributesBuilder.putAttributesFrom(map: Map<String, Any?>): AttributesBuilder =
    apply {
        map.forEach { (key, value) ->
            if (key.startsWith("pulse.internal")) return@forEach
            when (value) {
                is Attributes -> {
                    putAll(value)
                }

                is Int -> {
                    put(key, value.toLong())
                }

                is Long -> {
                    put(key, value)
                }

                is Double -> {
                    put(key, value)
                }

                is Boolean -> {
                    put(key, value)
                }

                is String -> {
                    put(key, value)
                }

                else -> {
                    put(key, value?.toString())
                }
            }
        }
    }

public fun Map<String, Any?>.toAttributes(): Attributes = (Attributes.builder() putAttributesFrom this).build()

public fun Attributes.filterNot(predicate: (AttributeKey<*>) -> Boolean): Attributes = this.toBuilder().removeIf(predicate).build()

public fun Attributes.filter(predicate: (AttributeKey<*>) -> Boolean): Attributes = this.toBuilder().removeIf { !predicate(it) }.build()

internal val regexCache = ConcurrentHashMap<String, ThreadLocal<Matcher>>()

public fun String.matchesFromRegexCache(regexStr: String): Boolean {
    val threadLocalMatcher =
        regexCache.computeIfAbsent(regexStr) {
            val pattern = Pattern.compile(regexStr)
            object : ThreadLocal<Matcher>() {
                override fun initialValue(): Matcher = pattern.matcher("")
            }
        }
    val matcher = threadLocalMatcher.get() ?: error("matcher should not be null here")
    matcher.reset(this)
    return matcher.matches()
}
