package com.pulse.android.sdk.internal

import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.PulseLogger
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity

/**
 * Resolves which endpoint connectivity to use for each signal type.
 *
 * When a [PulseSdkConfig] is present, the URLs from the config take precedence over
 * the caller-supplied connectivity objects. This allows server-side control of signal routing.
 */
internal object PulseEndpointUtils {
    internal data class ResolvedEndpoints(
        val span: EndpointConnectivity,
        val log: EndpointConnectivity,
        val metric: EndpointConnectivity,
        val customEvent: EndpointConnectivity,
    )

    internal fun resolve(
        sdkConfig: PulseSdkConfig?,
        headers: Map<String, String>,
        fallbackSpan: EndpointConnectivity,
        fallbackLog: EndpointConnectivity,
        fallbackMetric: EndpointConnectivity,
        fallbackCustomEvent: EndpointConnectivity,
    ): ResolvedEndpoints {
        val resolved =
            ResolvedEndpoints(
                span =
                    createEndpointConnectivity(
                        sdkConfig?.run { signals.spanCollectorUrl },
                        fallbackSpan.getUrl(),
                        headers,
                        fallbackSpan.getHeaders(),
                    ),
                log =
                    createEndpointConnectivity(
                        sdkConfig?.run { signals.logsCollectorUrl },
                        fallbackLog.getUrl(),
                        headers,
                        fallbackLog.getHeaders(),
                    ),
                metric =
                    createEndpointConnectivity(
                        sdkConfig?.run { signals.metricCollectorUrl },
                        fallbackMetric.getUrl(),
                        headers,
                        fallbackMetric.getHeaders(),
                    ),
                customEvent =
                    createEndpointConnectivity(
                        sdkConfig?.run { signals.customEventCollectorUrl },
                        fallbackCustomEvent.getUrl(),
                        headers,
                        fallbackCustomEvent.getHeaders(),
                    ),
            )
        PulseLogger.logDebug(TAG) { "spanCollectorUrl = ${PulseNetworkingUtils.redactUrl(resolved.span.getUrl())}" }
        PulseLogger.logDebug(TAG) { "logsCollectorUrl = ${PulseNetworkingUtils.redactUrl(resolved.log.getUrl())}" }
        PulseLogger.logDebug(TAG) { "metricCollectorUrl = ${PulseNetworkingUtils.redactUrl(resolved.metric.getUrl())}" }
        PulseLogger.logDebug(TAG) { "customEventCollectorUrl = ${PulseNetworkingUtils.redactUrl(resolved.customEvent.getUrl())}" }
        return resolved
    }

    private fun createEndpointConnectivity(
        url: String?,
        fallbackUrl: String,
        headers: Map<String, String>,
        fallbackHeaders: Map<String, String>,
    ): HttpEndpointConnectivity =
        if (url != null) {
            HttpEndpointConnectivity(url = url, headers = headers)
        } else {
            HttpEndpointConnectivity(url = fallbackUrl, headers = fallbackHeaders + headers)
        }

    private const val TAG = "PulseEndpointUtils"
}
