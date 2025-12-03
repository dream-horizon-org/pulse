@file:OptIn(Incubating::class)

package com.pulse.android.sdk

import android.app.Application
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.api.common.Attributes

/**
 * Interface defining the public API for the PulseSDK
 */
public interface PulseSDK {
    public fun isInitialized(): Boolean

    /**
     * Initialize the Pulse SDK, multiple call to the SDK will be ignored
     * Call this method at the earliest so that info related app start can be captured more
     * accurately
     */
    public fun initialize(
        application: Application,
        endpointBaseUrl: String,
        endpointHeaders: Map<String, String> = emptyMap(),
        spanEndpointConnectivity: EndpointConnectivity =
            HttpEndpointConnectivity.forTraces(
                endpointBaseUrl,
                endpointHeaders,
            ),
        logEndpointConnectivity: EndpointConnectivity =
            HttpEndpointConnectivity.forLogs(
                endpointBaseUrl,
                endpointHeaders,
            ),
        metricEndpointConnectivity: EndpointConnectivity =
            HttpEndpointConnectivity.forMetrics(
                endpointBaseUrl,
                endpointHeaders,
            ),
        sessionConfig: SessionConfig = SessionConfig.withDefaults(),
        globalAttributes: (() -> Attributes)? = null,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)? = null,
        instrumentations: (InstrumentationConfiguration.() -> Unit)? = null,
    )

    /**
     * Set user id for the session. Setting null will reset the id
     * Also see [setUserProperty]
     */
    public fun setUserId(id: String?)

    /**
     * Set user property for this session
     * Also see [setUserId]
     */
    public fun setUserProperty(
        name: String,
        value: Any?,
    )

    /**
     * Set user properties for this session
     * Also see [setUserProperty] and [setUserId]
     */
    public fun setUserProperties(builderAction: MutableMap<String, Any>.() -> Unit)

    public fun trackEvent(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?> = emptyMap(),
    )

    public fun trackNonFatal(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?> = emptyMap(),
    )

    public fun trackNonFatal(
        throwable: Throwable,
        observedTimeStampInMs: Long,
        params: Map<String, Any?> = emptyMap(),
    )

    /**
     * Starts the span, executes the action and then close the span automatically.
     * Also see [startSpan]
     */
    public fun <T> trackSpan(
        spanName: String,
        params: Map<String, Any?> = emptyMap(),
        action: () -> T,
    )

    /**
     * Starts the span and returns a callback which can be invoked to close the span
     */
    public fun startSpan(
        spanName: String,
        params: Map<String, Any?> = emptyMap(),
    ): () -> Unit

    public fun getOtelOrNull(): OpenTelemetryRum?

    public fun getOtelOrThrow(): OpenTelemetryRum

    public companion object {
        @JvmStatic
        public val INSTANCE: PulseSDK by lazy { PulseSDKImpl() }
    }
}
