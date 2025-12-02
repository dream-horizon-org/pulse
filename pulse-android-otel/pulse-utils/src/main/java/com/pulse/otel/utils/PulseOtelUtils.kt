package com.pulse.otel.utils

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder

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

public fun Map<String, Any?>.toAttributes(): Attributes = (Attributes.builder() putAttributesFrom this).build()
