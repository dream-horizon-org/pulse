@file:OptIn(Incubating::class)

package com.pulse.android.sdk

import android.app.Application
import com.pulse.android.sdk.internal.PulseSDKInternal
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.ResourceBuilder

/**
 * Interface defining the public API for the PulseSDK
 */
@Suppress("ComplexInterface")
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
        projectId: String,
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
        /**
         * Endpoint connectivity for custom business events. This will control the endpoint url for [trackEvent]
         * If not provided, [logEndpointConnectivity] will be used
         */
        customEventConnectivity: EndpointConnectivity = logEndpointConnectivity,
        /**
         * Optional custom URL for fetching SDK configuration.
         * If not provided, defaults to: {endpointBaseUrl with port 8080}/v1/configs/active/
         */
        configEndpointUrl: String? = null,
        resource: (ResourceBuilder.() -> Unit)? = null,
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
     * Set user property for this session. Passing null will remove the property from the key
     * Also see [setUserId]
     */
    public fun setUserProperty(
        name: String,
        value: Any?,
    )

    /**
     * Set user properties for this session. Passing null will remove the property from the key
     * Also see [setUserProperty] and [setUserId]
     */
    public fun setUserProperties(builderAction: MutableMap<String, Any?>.() -> Unit)

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

    /**
     * Shuts down the Pulse SDK: flushes and releases OpenTelemetry resources and uninstalls
     * instrumentation. After shutdown, the SDK cannot be re-initialized in this process.
     */
    public fun shutdown()

    public companion object {
        @JvmStatic
        public val INSTANCE: PulseSDK by lazy {
            PulseSDKAdapter(PulseSDKInternal())
        }
    }
}
