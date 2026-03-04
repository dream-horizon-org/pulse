package com.pulsereactnativeotel

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.pulse.android.sdk.internal.PulseSDKInternal
import android.content.Context
import android.os.Looper
import android.util.Log
import android.os.Handler
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseFeatureName
import kotlinx.serialization.json.Json

@ReactModule(name = PulseReactNativeOtelModule.NAME)
internal class PulseReactNativeOtelModule(reactContext: ReactApplicationContext) :
  NativePulseReactNativeOtelSpec(reactContext) {

  override fun getName(): String {
    return NAME
  }

  override fun isInitialized(): Boolean {
    return Pulse.sdkInternal.isInitialized()
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
    Pulse.sdkInternal.setUserId(id)
  }

  override fun setUserProperty(name: String, value: String?) {
    Pulse.sdkInternal.setUserProperty(name, value)
  }

  override fun setUserProperties(properties: ReadableMap?) {
    properties?.let { props ->
      Pulse.sdkInternal.setUserProperties {
        props.entryIterator.forEach { (key, value) ->
          put(key, value)
        }
      }
    }
  }

  override fun shutdown(): Boolean {
    Pulse.sdkInternal.shutdown()
    return true
  }

  override fun triggerAnr() {
    Handler(Looper.getMainLooper()).postAtFrontOfQueue {
      Log.d("[Pulse]", "Now running PostAtFrontQueue: ${Thread.currentThread().name}")
      Thread.sleep(10_000)
    }
  }

  override fun getAllFeatures(): WritableMap? {
    val context = reactApplicationContext
    val sharedPrefs = context.getSharedPreferences(
      "pulse_sdk_config",
      Context.MODE_PRIVATE
    )

    val configJson = sharedPrefs.getString("sdk_config", null) ?: return null

    val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
    }
    val config = json.decodeFromString<PulseSdkConfig>(configJson)

    val features = Arguments.createMap()
    val featureMap = mutableMapOf<String, Boolean>()

    config.features.forEach { featureConfig ->
      if (PulseSdkName.ANDROID_RN in featureConfig.sdks) {
        if (featureConfig.featureName == PulseFeatureName.UNKNOWN) {
          return@forEach
        }

        val featureNameStr = featureConfig.featureName.name.lowercase()
        val isEnabled = featureConfig.sessionSampleRate > 0F
        featureMap[featureNameStr] = isEnabled
      }
    }

    val requiredFeatures = listOf(
      "rn_screen_load",
      "screen_session",
      "rn_screen_interactive",
      "network_instrumentation",
      "custom_events",
      "js_crash"
    )

    requiredFeatures.forEach { featureName ->
      features.putBoolean(featureName, featureMap[featureName] ?: false)
    }

    return features
  }

  companion object {
    const val NAME = "PulseReactNativeOtel"
  }
}
