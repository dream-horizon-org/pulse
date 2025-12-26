package com.pulsereactnativeotel

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.pulse.android.sdk.PulseSDK
import android.os.Looper
import android.util.Log
import android.os.Handler

@ReactModule(name = PulseReactNativeOtelModule.NAME)
class PulseReactNativeOtelModule(reactContext: ReactApplicationContext) :
  NativePulseReactNativeOtelSpec(reactContext) {

  override fun getName(): String {
    return NAME
  }

  override fun isInitialized(): Boolean {
    return PulseSDK.INSTANCE.isInitialized()
  }

  override fun setCurrentScreenName(screenName: String): Boolean {
    ReactNativeScreenNameTracker.setCurrentScreenName(screenName)
    return true
  }

  override fun trackEvent(event: String, observedTimeMs: Double, properties: ReadableMap?): Boolean {
    PulseReactNativeOtelLogger.trackEvent(event, observedTimeMs.toLong(), properties)
    return true
  }

  override fun reportException(errorMessage: String, observedTimeMs: Double, stackTrace: String, isFatal: Boolean, errorType: String, attributes: ReadableMap?): Boolean {
    PulseReactNativeOtelLogger.reportException(errorMessage, observedTimeMs.toLong(), stackTrace, isFatal, errorType, attributes)
    return true
  }

  override fun startSpan(name: String, inheritContext: Boolean, attributes: ReadableMap?): String {
    return PulseReactNativeOtelTracer.startSpan(name, inheritContext, attributes)
  }

  override fun endSpan(spanId: String, statusCode: String?): Boolean {
    PulseReactNativeOtelTracer.endSpan(spanId, statusCode)
    return true
  }

  override fun addSpanEvent(spanId: String, name: String, attributes: ReadableMap?): Boolean {
    PulseReactNativeOtelTracer.addEvent(spanId, name, attributes)
    return true
  }

  override fun setSpanAttributes(spanId: String, attributes: ReadableMap?): Boolean {
    PulseReactNativeOtelTracer.setAttributes(spanId, attributes)
    return true
  }

  override fun recordSpanException(spanId: String, errorMessage: String, stackTrace: String?): Boolean {
    PulseReactNativeOtelTracer.recordException(spanId, errorMessage, stackTrace)
    return  true
  }

  override fun discardSpan(spanId: String): Boolean {
    PulseReactNativeOtelTracer.discardSpan(spanId)
    return true
  }

  override fun setUserId(id: String?) {
    PulseSDK.INSTANCE.setUserId(id)
  }

  override fun setUserProperty(name: String, value: String?) {
    PulseSDK.INSTANCE.setUserProperty(name, value)
  }

  override fun setUserProperties(properties: ReadableMap?) {
    properties?.let { props ->
      PulseSDK.INSTANCE.setUserProperties {
        props.entryIterator.forEach { (key, value) ->
          put(key, value)
        }
      }
    }
  }

  override fun triggerAnr() {
    Handler(Looper.getMainLooper()).postAtFrontOfQueue {
      Log.d("[Pulse]", "Now running PostAtFrontQueue: ${Thread.currentThread().name}")
      Thread.sleep(10_000)
    }
  }

  companion object {
    const val NAME = "PulseReactNativeOtel"
  }
}
