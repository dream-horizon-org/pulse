@file:OptIn(Incubating::class)

package com.pulse.android.sdk

import android.app.Application
import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.PulseDataCollectionConsent
import com.pulse.android.sdk.internal.PulseSDKInternal
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.ResourceBuilder

/**
 * Adapter that implements PulseSDK by delegating to the internal implementation.
 * This allows pulse-android-sdk to provide INSTANCE without creating a cyclic dependency
 * with pulse-android-sdk-internal (which does not depend on PulseSDK interface).
 */
internal class PulseSDKAdapter(
    private val delegate: PulseSDKInternal,
) : PulseSDK {
    override fun isInitialized(): Boolean = delegate.isInitialized()

    override fun initialize(
        application: Application,
        endpointBaseUrl: String,
        projectId: String,
        dataCollectionState: PulseDataCollectionConsent,
        endpointHeaders: Map<String, String>,
        spanEndpointConnectivity: EndpointConnectivity,
        logEndpointConnectivity: EndpointConnectivity,
        metricEndpointConnectivity: EndpointConnectivity,
        customEventConnectivity: EndpointConnectivity,
        configEndpointUrl: String?,
        resource: (ResourceBuilder.() -> Unit)?,
        sessionConfig: SessionConfig,
        globalAttributes: (() -> Attributes)?,
        beforeSendData: PulseBeforeSendData?,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)?,
        instrumentations: (InstrumentationConfiguration.() -> Unit)?,
    ) {
        delegate.initialize(
            application = application,
            endpointBaseUrl = endpointBaseUrl,
            projectId = projectId,
            endpointHeaders = endpointHeaders,
            spanEndpointConnectivity = spanEndpointConnectivity,
            logEndpointConnectivity = logEndpointConnectivity,
            metricEndpointConnectivity = metricEndpointConnectivity,
            customEventConnectivity = customEventConnectivity,
            configEndpointUrl = configEndpointUrl,
            resource = resource,
            sessionConfig = sessionConfig,
            globalAttributes = globalAttributes,
            diskBuffering = diskBuffering,
            instrumentations = instrumentations,
            tracerProviderCustomizer = null,
            loggerProviderCustomizer = null,
            beforeSendData = beforeSendData,
            dataCollectionState = dataCollectionState,
        )
    }

    override fun setDataCollectionState(newState: PulseDataCollectionConsent) {
        delegate.setDataCollectionState(newState)
    }

    override fun setUserId(id: String?) {
        delegate.setUserId(id)
    }

    override fun setUserProperty(
        name: String,
        value: Any?,
    ) {
        delegate.setUserProperty(name, value)
    }

    override fun setUserProperties(builderAction: MutableMap<String, Any?>.() -> Unit) {
        delegate.setUserProperties(builderAction)
    }

    override fun trackEvent(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        delegate.trackEvent(name, observedTimeStampInMs, params)
    }

    override fun trackNonFatal(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        delegate.trackNonFatal(name, observedTimeStampInMs, params)
    }

    override fun trackNonFatal(
        throwable: Throwable,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        delegate.trackNonFatal(throwable, observedTimeStampInMs, params)
    }

    override fun <T> trackSpan(
        spanName: String,
        params: Map<String, Any?>,
        action: () -> T,
    ) {
        delegate.trackSpan(spanName, params, action)
    }

    override fun startSpan(
        spanName: String,
        params: Map<String, Any?>,
    ): () -> Unit = delegate.startSpan(spanName, params)

    override fun shutdown() {
        delegate.shutdown()
    }

    override fun getOtelOrNull(): OpenTelemetryRum? = delegate.getOtelOrNull()

    override fun getOtelOrThrow(): OpenTelemetryRum = delegate.getOtelOrThrow()
}
