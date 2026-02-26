@file:OptIn(io.opentelemetry.android.Incubating::class)

package com.pulsereactnativeotel

import android.app.Application
import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.sdk.internal.PulseSDKInternal
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
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
    @JvmStatic
    public fun initialize(
        application: Application,
        endpointBaseUrl: String,
        projectId: String,
        endpointHeaders: Map<String, String> = emptyMap(),
        spanEndpointConnectivity: EndpointConnectivity = HttpEndpointConnectivity.forTraces(endpointBaseUrl, endpointHeaders),
        logEndpointConnectivity: EndpointConnectivity = HttpEndpointConnectivity.forLogs(endpointBaseUrl, endpointHeaders),
        metricEndpointConnectivity: EndpointConnectivity = HttpEndpointConnectivity.forMetrics(endpointBaseUrl, endpointHeaders),
        customEventConnectivity: EndpointConnectivity = logEndpointConnectivity,
        configEndpointUrl: String? = null,
        resource: (ResourceBuilder.() -> Unit)? = null,
        sessionConfig: SessionConfig = SessionConfig.withDefaults(),
        globalAttributes: (() -> Attributes)? = null,
        beforeSendData: PulseBeforeSendData? = null,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)? = null,
        instrumentations: (InstrumentationConfiguration.() -> Unit)? = null,
    ) {
        val rnTracerProviderCustomizer = BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, _ ->
            tracerProviderBuilder.addSpanProcessor(ReactNativeScreenAttributesSpanProcessor())
        }

        val rnLoggerProviderCustomizer = BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, _ ->
            loggerProviderBuilder.addLogRecordProcessor(ReactNativeScreenAttributesLogRecordProcessor())
        }

        // Set telemetry.sdk.name for React Native SDK (read in OpenTelemetryRumInitializer for sampling)
        val rnResource: (ResourceBuilder.() -> Unit) = {
            put(PulseAttributes.TELEMETRY_SDK_NAME_KEY, PulseAttributes.PulseSdkNames.ANDROID_RN)
            resource?.invoke(this)
        }

        sdkInternal.initialize(
            application = application,
            endpointBaseUrl = endpointBaseUrl,
            projectId = projectId,
            endpointHeaders = endpointHeaders,
            spanEndpointConnectivity = spanEndpointConnectivity,
            logEndpointConnectivity = logEndpointConnectivity,
            metricEndpointConnectivity = metricEndpointConnectivity,
            customEventConnectivity = customEventConnectivity,
            configEndpointUrl = configEndpointUrl,
            resource = rnResource,
            sessionConfig = sessionConfig,
            globalAttributes = globalAttributes,
            diskBuffering = diskBuffering,
            tracerProviderCustomizer = rnTracerProviderCustomizer,
            loggerProviderCustomizer = rnLoggerProviderCustomizer,
            instrumentations = instrumentations,
            beforeSendData = beforeSendData,
        )
    }
}

