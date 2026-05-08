@file:OptIn(Incubating::class)

package com.pulsereactnativeotel

import android.app.Application
import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.PulseDataCollectionConsent
import com.pulse.android.sdk.internal.PulseSDKInternal
import com.pulse.semconv.PulseAttributes
import com.pulse.utils.PulseLogLevel
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.resources.ResourceBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import java.util.function.BiFunction

/**
 * React Native wrapper for PulseSDK that automatically adds React Native screen name processors.
 * This ensures React Native screen names override Android Activity/Fragment names in telemetry.
 */
public object Pulse {
    internal val sdkInternal by lazy { PulseSDKInternal() }

    /** Whether [initialize] completed successfully and [shutdown] has not been called. */
    @JvmStatic
    public fun isInitialized(): Boolean = sdkInternal.isInitialized()

    @JvmStatic
    public fun initialize(
        application: Application,
        apiKey: String,
        dataCollectionState: PulseDataCollectionConsent,
        resource: (ResourceBuilder.() -> Unit)? = null,
        globalAttributes: (() -> Attributes)? = null,
        beforeSendData: PulseBeforeSendData? = null,
        logLevel: PulseLogLevel = PulseLogLevel.NONE,
        instrumentations: (InstrumentationConfiguration.() -> Unit)? = null,
    ) {
        val rnTracerProviderCustomizer = BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, _ ->
            tracerProviderBuilder.addSpanProcessor(ReactNativeScreenAttributesSpanProcessor())
        }

        val rnLoggerProviderCustomizer = BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, _ ->
            loggerProviderBuilder.addLogRecordProcessor(ReactNativeScreenAttributesLogRecordProcessor())
        }

        // Set telemetry.sdk.name for the React Native SDK (read in OpenTelemetryRumInitializer for sampling)
        val rnResource: (ResourceBuilder.() -> Unit) = {
            put(PulseAttributes.TELEMETRY_SDK_NAME_KEY, PulseAttributes.PulseSdkNames.ANDROID_RN)
            resource?.invoke(this)
        }

        sdkInternal.initialize(
            application = application,
            apiKey = apiKey,
            dataCollectionState = dataCollectionState,
            resource = rnResource,
            globalAttributes = globalAttributes,
            beforeSendData = beforeSendData,
            tracerProviderCustomizer = rnTracerProviderCustomizer,
            loggerProviderCustomizer = rnLoggerProviderCustomizer,
            logLevel = logLevel,
            instrumentations = instrumentations,
        )
    }

    /**
     * Stops export, flushes session replay if enabled, and tears down OpenTelemetry. Further SDK calls are no-ops or throw per API.
     */
    @JvmStatic
    public fun shutdown() {
        sdkInternal.shutdown()
    }

    /**
     * Updates the data collection consent state. See [PulseDataCollectionConsent] for all the allowed values
     */
    @JvmStatic
    public fun setDataCollectionState(newState: PulseDataCollectionConsent) {
        sdkInternal.setDataCollectionState(newState)
    }

    @JvmStatic
    public fun setUserId(id: String?) {
        sdkInternal.setUserId(id)
    }

    @JvmStatic
    public fun setUserProperty(
        name: String,
        value: Any?,
    ) {
        sdkInternal.setUserProperty(name, value)
    }

    @JvmStatic
    public fun setUserProperties(properties: Map<String, Any?>) {
        sdkInternal.setUserProperties(properties)
    }

    @JvmStatic
    public fun setUserProperties(builderAction: MutableMap<String, Any?>.() -> Unit) {
        sdkInternal.setUserProperties(builderAction)
    }

    @JvmStatic
    public fun trackEvent(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        sdkInternal.trackEvent(name, observedTimeStampInMs, params)
    }

    @JvmStatic
    public fun trackNonFatal(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        sdkInternal.trackNonFatal(name, observedTimeStampInMs, params)
    }

    @JvmStatic
    public fun trackNonFatal(
        throwable: Throwable,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        sdkInternal.trackNonFatal(throwable, observedTimeStampInMs, params)
    }

    @JvmStatic
    public fun trackSpan(
        spanName: String,
        params: Map<String, Any?>,
        action: () -> Unit,
    ) {
        sdkInternal.trackSpan(spanName, params, action)
    }

    /**
     * Builds a span with [params] as attributes. Returns a function that must be invoked to end the span (no-op if SDK not initialized).
     */
    @JvmStatic
    public fun startSpan(
        spanName: String,
        params: Map<String, Any?>,
    ): () -> Unit = sdkInternal.startSpan(spanName, params)

    /** OpenTelemetry RUM instance after successful init, or null if not initialized or after shutdown. */
    @JvmStatic
    public fun getOtelOrNull(): OpenTelemetryRum? = sdkInternal.getOtelOrNull()

    /** [OpenTelemetryRum] after init; throws if not initialized or shut down. */
    @JvmStatic
    public fun getOtelOrThrow(): OpenTelemetryRum = sdkInternal.getOtelOrThrow()
}
