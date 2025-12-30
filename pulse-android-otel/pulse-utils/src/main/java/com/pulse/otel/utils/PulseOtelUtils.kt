package com.pulse.otel.utils

import android.util.Log
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

public object PulseOtelUtils {
    // todo when https://github.com/open-telemetry/opentelemetry-android/issues/1393 is fixed
    //  use the new not deprecated attributes
    @Suppress("DEPRECATION")
    private val HTTP_METHOD_KEY: AttributeKey<String> = HttpIncubatingAttributes.HTTP_METHOD
    private const val HEX_CHARS = "[0-9a-fA-F]"
    private const val DIGITS = "\\d"
    private const val ALPHANUMERIC = "[A-Za-z0-9]"
    private const val ULID_CHARS = "[0-9A-HJKMNP-TV-Z]"
    private const val REDACTED = "[redacted]"

    private val urlNormalizationPatterns =
        listOf(
            "(?<=/)($HEX_CHARS{64}|$HEX_CHARS{40})(?=/|$)".toRegex(),
            "(?<=/)($HEX_CHARS{32}|$HEX_CHARS{8}-$HEX_CHARS{4}-$HEX_CHARS{4}-$HEX_CHARS{4}-$HEX_CHARS{12})(?=/|$)".toRegex(),
            "(?<=/)($HEX_CHARS{24})(?=/|$)".toRegex(),
            "(?<=/)($ULID_CHARS{26})(?=/|$)".toRegex(),
            "(?<=/)($DIGITS{3,})(?=/|$)".toRegex(),
            "(?<=/)($ALPHANUMERIC{16,})(?=/|$)".toRegex(),
        )

    public fun isNetworkSpan(span: ReadableSpan): Boolean = span.attributes.get(HTTP_METHOD_KEY) != null

    public fun normaliseUrl(originalUrl: String): String {
        var normalized = originalUrl.substringBefore("?")

        urlNormalizationPatterns.forEach { pattern ->
            normalized = pattern.replace(normalized, REDACTED)
        }

        return normalized
    }

    public inline fun logError(
        tag: String,
        throwable: Throwable,
        body: () -> String,
    ) {
        Log.e("$TAG:$tag", body(), throwable)
    }

    public inline fun logDebug(
        tag: String,
        body: () -> String,
    ) {
        Log.d("$TAG:$tag", body())
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

@PublishedApi
internal const val TAG: String = "PulseOtelSdk"

public fun Map<String, Any?>.toAttributes(): Attributes = (Attributes.builder() putAttributesFrom this).build()

public fun Attributes.toMap(): Map<String, Any?> = this.asMap().mapKeys { it.key.key }

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
