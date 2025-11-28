package com.pulse.otel.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.trace.ReadableSpan

public object PulseOtelUtils {
    private val HTTP_METHOD_KEY: AttributeKey<String> = AttributeKey.stringKey("http.method")
    
    public fun isNetworkSpan(span: ReadableSpan): Boolean {
        if (span.name.startsWith("HTTP ")) {
            return true
        }
        // Fallback: check http.method attribute
        return span.attributes.get(HTTP_METHOD_KEY) != null
    }

    public fun normaliseUrl(originalUrl: String): String {
        // Remove query parameters
        val urlWithoutQuery = originalUrl.substringBefore("?")

        // Replace UUIDs with {uuid}
        val allowedCharsInUuid = """[0-9a-fA-F]"""
        val uuidPattern = Regex("""$allowedCharsInUuid{8}-$allowedCharsInUuid{4}-$allowedCharsInUuid{4}-$allowedCharsInUuid{4}-$allowedCharsInUuid{12}|$allowedCharsInUuid{32}""")
        return uuidPattern.replace(urlWithoutQuery) { "{uuid}" }
    }
}

public infix fun AttributesBuilder.putAttributesFrom(map: Map<String, Any?>): AttributesBuilder =
    apply {
        map.forEach { (key, value) ->
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
                    put(key, value.toString())
                }
            }
        }
    }

public fun Map<String, Any?>.toAttributes(): Attributes =
    (Attributes.builder() putAttributesFrom this).build()