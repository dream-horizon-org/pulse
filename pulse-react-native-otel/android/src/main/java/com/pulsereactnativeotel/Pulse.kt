@file:OptIn(io.opentelemetry.android.Incubating::class)

package com.pulsereactnativeotel

import android.app.Application
import com.pulse.android.sdk.PulseSDK
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import java.util.function.BiFunction

/**
 * React Native wrapper for PulseSDK that automatically adds React Native screen name processors.
 * This ensures React Native screen names override Android Activity/Fragment names in telemetry.
 */
public object Pulse : PulseSDK by PulseSDK.INSTANCE {

    public override fun initialize(
        application: Application,
        endpointBaseUrl: String,
        endpointHeaders: Map<String, String>,
        spanEndpointConnectivity: EndpointConnectivity,
        logEndpointConnectivity: EndpointConnectivity,
        metricEndpointConnectivity: EndpointConnectivity,
        sessionConfig: SessionConfig,
        globalAttributes: (() -> Attributes)?,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)?,
        resource: (io.opentelemetry.sdk.resources.ResourceBuilder.() -> Unit)?,
        tracerProviderCustomizer: BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>?,
        loggerProviderCustomizer: BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>?,
        instrumentations: (InstrumentationConfiguration.() -> Unit)?,
    ) {
        val rnTracerProviderCustomizer = BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, _ ->
            tracerProviderBuilder.addSpanProcessor(ReactNativeScreenAttributesSpanProcessor())
        }

        val rnLoggerProviderCustomizer = BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, _ ->
            loggerProviderBuilder.addLogRecordProcessor(ReactNativeScreenAttributesLogRecordProcessor())
        }

        val mergedTracerProviderCustomizer = if (tracerProviderCustomizer != null) {
            BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, application ->
                val builderWithRn = rnTracerProviderCustomizer.apply(tracerProviderBuilder, application)
                tracerProviderCustomizer.apply(builderWithRn, application)
            }
        } else {
            rnTracerProviderCustomizer
        }

        val mergedLoggerProviderCustomizer = if (loggerProviderCustomizer != null) {
            BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, application ->
                val builderWithRn = rnLoggerProviderCustomizer.apply(loggerProviderBuilder, application)
                loggerProviderCustomizer.apply(builderWithRn, application)
            }
        } else {
            rnLoggerProviderCustomizer
        }

        val rnResource: (io.opentelemetry.sdk.resources.ResourceBuilder.() -> Unit) = {
            put(AttributeKey.stringKey("telemetry.sdk.name"), "pulse-android-rn")
            resource?.invoke(this)
        }

        PulseSDK.INSTANCE.initialize(
            application = application,
            endpointBaseUrl = endpointBaseUrl,
            endpointHeaders = endpointHeaders,
            spanEndpointConnectivity = spanEndpointConnectivity,
            logEndpointConnectivity = logEndpointConnectivity,
            metricEndpointConnectivity = metricEndpointConnectivity,
            sessionConfig = sessionConfig,
            globalAttributes = globalAttributes,
            diskBuffering = diskBuffering,
            resource = rnResource,
            tracerProviderCustomizer = mergedTracerProviderCustomizer,
            loggerProviderCustomizer = mergedLoggerProviderCustomizer,
            instrumentations = instrumentations,
        )
    }
}

