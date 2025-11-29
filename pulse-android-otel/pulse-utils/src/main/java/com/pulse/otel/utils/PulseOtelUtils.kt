package com.pulse.otel.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.trace.ReadableSpan

public object PulseOtelUtils {
    private val HTTP_METHOD_KEY: AttributeKey<String> = AttributeKey.stringKey("http.method")
    
    // Regex pattern constants
    private const val HEX_CHARS = """[0-9a-fA-F]"""
    private const val DIGITS = """\d"""
    private const val ALPHANUMERIC = """[A-Za-z0-9]"""
    
    public fun isNetworkSpan(span: ReadableSpan): Boolean {
        return span.attributes.get(HTTP_METHOD_KEY) != null
    }

    public fun normaliseUrl(originalUrl: String): String {
        //Removed query-params
        var normalized = originalUrl.substringBefore("?")
        
        // Check longer patterns first to avoid partial matches
        // Git commit hashes (64 or 40 hex chars)
        val gitHashPattern = Regex("""$HEX_CHARS{64}|$HEX_CHARS{40}""")
        normalized = gitHashPattern.replace(normalized) { "[redacted]" }
        
        // UUIDs (32 hex chars without hyphens, or with hyphens)
        val uuidPattern = Regex("""$HEX_CHARS{32}|$HEX_CHARS{8}-$HEX_CHARS{4}-$HEX_CHARS{4}-$HEX_CHARS{4}-$HEX_CHARS{12}""")
        normalized = uuidPattern.replace(normalized) { "[redacted]" }
        
        // MongoDB ObjectIds (24 hex chars)
        val mongoObjectIdPattern = Regex("""$HEX_CHARS{24}""")
        normalized = mongoObjectIdPattern.replace(normalized) { "[redacted]" }
        
        // Numeric IDs (3+ digits in path segments)
        val numericIdPattern = Regex("""/($DIGITS{3,})""")
        normalized = numericIdPattern.replace(normalized) { "/[redacted]" }
        
        // Long alphanumeric strings (16+ chars, likely IDs)
        val longAlphanumericPattern = Regex("""/$ALPHANUMERIC{16,}(?=/|$)""")
        normalized = longAlphanumericPattern.replace(normalized) { "/[redacted]" }
        
        return normalized
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