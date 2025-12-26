package com.pulsereactnativeotel

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableType
import com.pulse.android.sdk.PulseSDK
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PulseReactNativeOtelTracer {

    private val tracer: Tracer by lazy {
        PulseSDK.INSTANCE.getOtelOrThrow()
            .getOpenTelemetry()
            .tracerProvider
            .tracerBuilder(PulseOtelConstants.INSTRUMENTATION_SCOPE)
            .build()
    }

    private val idToSpan = ConcurrentHashMap<String, Span>()
    private val idToScope = ConcurrentHashMap<String, Scope>()

    fun startSpan(name: String, inheritContext: Boolean, attributes: ReadableMap?): String {
        val span = tracer.spanBuilder(name)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()

        attributes?.applyTo(span)

        val id = UUID.randomUUID().toString()
        idToSpan[id] = span
        
        if (inheritContext) {
            val scope = span.makeCurrent()
            idToScope[id] = scope
        }

        return id
    }

    fun addEvent(spanId: String, name: String, attributes: ReadableMap?) {
        idToSpan[spanId]?.let { span ->
            attributes?.applyTo(span)
            span.addEvent(name)
        }
    }

    fun setAttributes(spanId: String, attributes: ReadableMap?) {
        idToSpan[spanId]?.let { span ->
            attributes?.applyTo(span)
        }
    }

    fun recordException(spanId: String, errorMessage: String, stackTrace: String?) {
        idToSpan[spanId]?.let { span ->
            val throwable = RuntimeException(errorMessage)
            span.recordException(throwable)
            span.setAttribute(
                AttributeKey.stringKey(PulseOtelConstants.ATTR_ERROR_MESSAGE),
                errorMessage
            )

            stackTrace?.takeIf { it.isNotEmpty() }?.let {
                span.setAttribute(
                    AttributeKey.stringKey(PulseOtelConstants.ATTR_ERROR_STACK),
                    it
                )
            }
        }
    }

    fun endSpan(spanId: String, statusCode: String?) {
        idToSpan.remove(spanId)?.let { span ->
            when (statusCode?.uppercase()) {
                "OK" -> span.setStatus(StatusCode.OK)
                "ERROR" -> span.setStatus(StatusCode.ERROR)
                else -> span.setStatus(StatusCode.UNSET)
            }
            span.end()
        }
        idToScope.remove(spanId)?.close()
    }

    fun discardSpan(spanId: String) {
        idToSpan.remove(spanId)?.let { span ->
            span.setAttribute(
                AttributeKey.booleanKey("pulse.internal"),
                true
            )
            span.end()
        }
        idToScope.remove(spanId)?.close()
    }

    private fun ReadableMap.applyTo(span: Span) {
        entryIterator.forEach { (key, value) ->
            when (value) {
                is String -> span.setAttribute(AttributeKey.stringKey(key), value)
                is Boolean -> span.setAttribute(AttributeKey.booleanKey(key), value)
                is Number -> span.setAttribute(AttributeKey.doubleKey(key), value.toDouble())
                is ReadableArray -> {
                    applyArrayAttribute(span, key, value)
                }
                else -> {
                    span.setAttribute(AttributeKey.stringKey(key), value.toString())
                }
            }
        }
    }

    private fun applyArrayAttribute(span: Span, key: String, array: ReadableArray) {
        if (array.size() == 0) {
            span.setAttribute(AttributeKey.stringArrayKey(key), emptyList())
            return
        }

        when (array.getType(0)) {
            ReadableType.String -> {
                span.setAttribute(AttributeKey.stringArrayKey(key), array.toArrayList() as List<String>)
            }
            ReadableType.Number -> {
                span.setAttribute(AttributeKey.doubleArrayKey(key), array.toArrayList() as List<Double>)
            }
            ReadableType.Boolean -> {
                span.setAttribute(AttributeKey.booleanArrayKey(key), array.toArrayList() as List<Boolean>)
            }
            else -> {
                span.setAttribute(AttributeKey.stringKey(key), array.toString())
            }
        }
    }
}


