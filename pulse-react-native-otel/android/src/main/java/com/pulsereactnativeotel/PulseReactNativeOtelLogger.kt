package com.pulsereactnativeotel

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.pulse.android.sdk.PulseSDK
import com.pulse.semconv.PulseAttributes

object PulseReactNativeOtelLogger {

    fun trackEvent(
        event: String,
        observedTimeMs: Long,
        properties: ReadableMap?
    ) {
        val params = buildMap {
            properties?.let { putAll(it.toMap()) }
        }

        PulseSDK.INSTANCE.trackEvent(event, observedTimeMs, params)
    }

    fun reportException(
        errorMessage: String,
        observedTimeMs: Long,
        stackTrace: String,
        isFatal: Boolean,
        errorType: String,
        attributes: ReadableMap?
    ) {
        val params = buildMap {
            put(PulseOtelConstants.ATTR_ERROR_TYPE, errorType.ifEmpty { PulseOtelConstants.DEFAULT_ERROR_TYPE })
            put(PulseOtelConstants.ATTR_ERROR_FATAL, isFatal)
            put(PulseOtelConstants.ATTR_ERROR_MESSAGE, errorMessage)
            put(PulseOtelConstants.ATTR_ERROR_STACK, stackTrace)
            put(PulseOtelConstants.ATTR_THREAD_ID, getCurrentThreadId())
            put(PulseOtelConstants.ATTR_THREAD_NAME, Thread.currentThread().name)
            put(PulseOtelConstants.ATTR_ERROR_SOURCE, PulseOtelConstants.ERROR_SOURCE_JS)
            put(PulseAttributes.PULSE_TYPE.key, if (isFatal) PulseAttributes.PulseTypeValues.CRASH else PulseAttributes.PulseTypeValues.NON_FATAL)
            attributes?.let { putAll(it.toMap()) }
        }

        PulseSDK.INSTANCE.trackNonFatal(errorMessage, observedTimeMs, params)
    }

    private fun getCurrentThreadId(): String {
        @Suppress("DEPRECATION")
        return Thread.currentThread().id.toString()
    }

    private fun ReadableMap.toMap(): Map<String, Any?> = buildMap {
        entryIterator.forEach { (key, value) ->
            put(key, value?.toAny())
        }
    }

    private fun Any.toAny(): Any = when (this) {
        is String -> this
        is Number -> this
        is Boolean -> this
        is ReadableArray -> this.toList()
        else -> this.toString()
    }

    private fun ReadableArray.toList(): List<Any?> {
        if (size() == 0) return emptyList()

        return when (getType(0)) {
            ReadableType.String -> toArrayList()
            ReadableType.Number -> toArrayList()
            ReadableType.Boolean -> toArrayList()
            else -> emptyList()
        }
    }
}
