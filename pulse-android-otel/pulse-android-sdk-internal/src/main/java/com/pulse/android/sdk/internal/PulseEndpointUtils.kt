package com.pulse.android.sdk.internal

import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.utils.PulseOtelUtils
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
            if (sdkConfig != null) {
                ResolvedEndpoints(
                    span = HttpEndpointConnectivity(url = sdkConfig.signals.spanCollectorUrl, headers = headers),
                    log = HttpEndpointConnectivity(url = sdkConfig.signals.logsCollectorUrl, headers = headers),
                    metric = HttpEndpointConnectivity(url = sdkConfig.signals.metricCollectorUrl, headers = headers),
                    customEvent = HttpEndpointConnectivity(url = sdkConfig.signals.customEventCollectorUrl, headers = headers),
                )
            } else {
                ResolvedEndpoints(
                    span = HttpEndpointConnectivity(url = fallbackSpan.getUrl(), headers = fallbackSpan.getHeaders() + headers),
                    log = HttpEndpointConnectivity(url = fallbackLog.getUrl(), headers = fallbackLog.getHeaders() + headers),
                    metric = HttpEndpointConnectivity(url = fallbackMetric.getUrl(), headers = fallbackMetric.getHeaders() + headers),
                    customEvent =
                        HttpEndpointConnectivity(
                            url = fallbackCustomEvent.getUrl(),
                            headers =
                                fallbackCustomEvent.getHeaders() + headers,
                        ),
                )
            }
        PulseOtelUtils.logDebug(TAG) { "spanCollectorUrl = ${resolved.span.getUrl()}" }
        PulseOtelUtils.logDebug(TAG) { "logsCollectorUrl = ${resolved.log.getUrl()}" }
        PulseOtelUtils.logDebug(TAG) { "metricCollectorUrl = ${resolved.metric.getUrl()}" }
        PulseOtelUtils.logDebug(TAG) { "customEventCollectorUrl = ${resolved.customEvent.getUrl()}" }
        return resolved
    }

    private const val TAG = "PulseEndpointUtils"
}
