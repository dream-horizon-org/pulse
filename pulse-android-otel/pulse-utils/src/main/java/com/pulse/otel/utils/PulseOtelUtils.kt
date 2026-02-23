package com.pulse.otel.utils

import android.util.Log
import com.pulse.utils.BuildConfig
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
    internal const val HEX_CHARS = "[0-9a-fA-F]"
    internal const val DIGITS = "\\d"
    internal const val ALPHANUMERIC = "[A-Za-z0-9]"
    internal const val ULID_CHARS = "[0-9A-HJKMNP-TV-Z]"
    internal const val REDACTED = "[redacted]"

    public fun isNetworkSpan(span: ReadableSpan): Boolean = span.attributes.get(HTTP_METHOD_KEY) != null

    public fun isDebug(): Boolean = BuildConfig.DEBUG

    @PublishedApi
    internal inline fun getTag(tag: () -> String): String = "$TAG:${tag()}"

    public inline fun logError(
        tag: String,
        throwable: Throwable,
        body: () -> String,
    ) {
        Log.e(getTag { tag }, body(), throwable)
    }

    public inline fun logDebug(
        tag: String,
        body: () -> String,
    ) {
        Log.d(getTag { tag }, body())
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
