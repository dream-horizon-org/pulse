package com.pulse.android.sdk.internal

import com.pulse.android.sdk.internal.PulseEndpointUtils.ResolvedEndpoints
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PulseEndpointUtilsTest {
    private val fallbackSpan = HttpEndpointConnectivity("https://fallback.example.com/v1/spans", emptyMap())
    private val fallbackLog = HttpEndpointConnectivity("https://fallback.example.com/v1/logs", emptyMap())
    private val fallbackMetric = HttpEndpointConnectivity("https://fallback.example.com/v1/metrics", emptyMap())
    private val fallbackCustomEvent = HttpEndpointConnectivity("https://fallback.example.com/v1/custom-events", emptyMap())
    val headers = mapOf("X-API-KEY" to "project-abc")

    @Nested
    inner class `when sdk config is absent` {
        private val resolved: ResolvedEndpoints =
            PulseEndpointUtils.resolve(
                sdkConfig = null,
                headers = emptyMap(),
                fallbackSpan = fallbackSpan,
                fallbackLog = fallbackLog,
                fallbackMetric = fallbackMetric,
                fallbackCustomEvent = fallbackCustomEvent,
            )

        @Test
        fun `span endpoint uses caller-supplied fallback`() {
            assertThat(resolved.span.getUrl()).isEqualTo(fallbackSpan.getUrl())
        }

        @Test
        fun `log endpoint uses caller-supplied fallback`() {
            assertThat(resolved.log.getUrl()).isEqualTo(fallbackLog.getUrl())
        }

        @Test
        fun `metric endpoint uses caller-supplied fallback`() {
            assertThat(resolved.metric.getUrl()).isEqualTo(fallbackMetric.getUrl())
        }

        @Test
        fun `custom-event endpoint uses caller-supplied fallback`() {
            assertThat(resolved.customEvent.getUrl()).isEqualTo(fallbackCustomEvent.getUrl())
        }

        @Test
        fun `all fallback endpoints carry the provided headers`() {
            val resolvedWithHeaders =
                PulseEndpointUtils.resolve(
                    sdkConfig = null,
                    headers = headers,
                    fallbackSpan = fallbackSpan,
                    fallbackLog = fallbackLog,
                    fallbackMetric = fallbackMetric,
                    fallbackCustomEvent = fallbackCustomEvent,
                )
            assertThat(resolvedWithHeaders.span.getHeaders()).containsEntry("X-API-KEY", "project-abc")
            assertThat(resolvedWithHeaders.log.getHeaders()).containsEntry("X-API-KEY", "project-abc")
            assertThat(resolvedWithHeaders.metric.getHeaders()).containsEntry("X-API-KEY", "project-abc")
            assertThat(resolvedWithHeaders.customEvent.getHeaders()).containsEntry("X-API-KEY", "project-abc")
        }

        @Test
        fun `fallback endpoints merge provided headers with their own existing headers`() {
            val fallbackWithOwnHeader =
                HttpEndpointConnectivity(
                    "https://fallback.example.com/v1/spans",
                    mapOf("X-Fallback-Header" to "fallback-value"),
                )
            val resolvedWithHeaders =
                PulseEndpointUtils.resolve(
                    sdkConfig = null,
                    headers = headers,
                    fallbackSpan = fallbackWithOwnHeader,
                    fallbackLog = fallbackLog,
                    fallbackMetric = fallbackMetric,
                    fallbackCustomEvent = fallbackCustomEvent,
                )
            assertThat(resolvedWithHeaders.span.getHeaders()).containsEntry("X-Fallback-Header", "fallback-value")
            assertThat(resolvedWithHeaders.span.getHeaders()).containsEntry("X-API-KEY", "project-abc")
        }

        @Test
        fun `fallback endpoints without own headers only contain the provided headers`() {
            val resolvedWithHeaders =
                PulseEndpointUtils.resolve(
                    sdkConfig = null,
                    headers = headers,
                    fallbackSpan = fallbackSpan,
                    fallbackLog = fallbackLog,
                    fallbackMetric = fallbackMetric,
                    fallbackCustomEvent = fallbackCustomEvent,
                )
            assertThat(resolvedWithHeaders.span.getHeaders()).containsOnlyKeys("X-API-KEY")
        }
    }

    @Nested
    inner class `when sdk config is present` {
        private val configUrl = "https://config.pulse.example.com/"
        private val sdkConfig =
            PulseSdkConfigFakeUtils.createFakeConfig(collectorUrl = configUrl)

        private val resolved: ResolvedEndpoints =
            PulseEndpointUtils.resolve(
                sdkConfig = sdkConfig,
                headers = emptyMap(),
                fallbackSpan = fallbackSpan,
                fallbackLog = fallbackLog,
                fallbackMetric = fallbackMetric,
                fallbackCustomEvent = fallbackCustomEvent,
            )

        @Test
        fun `span endpoint uses url from config, not from fallback`() {
            assertThat(resolved.span.getUrl()).isEqualTo(sdkConfig.signals.spanCollectorUrl)
            assertThat(resolved.span.getUrl()).isNotEqualTo(fallbackSpan.getUrl())
        }

        @Test
        fun `log endpoint uses url from config, not from fallback`() {
            assertThat(resolved.log.getUrl()).isEqualTo(sdkConfig.signals.logsCollectorUrl)
            assertThat(resolved.log.getUrl()).isNotEqualTo(fallbackLog.getUrl())
        }

        @Test
        fun `metric endpoint uses url from config, not from fallback`() {
            assertThat(resolved.metric.getUrl()).isEqualTo(sdkConfig.signals.metricCollectorUrl)
            assertThat(resolved.metric.getUrl()).isNotEqualTo(fallbackMetric.getUrl())
        }

        @Test
        fun `custom-event endpoint uses url from config, not from fallback`() {
            assertThat(resolved.customEvent.getUrl()).isEqualTo(sdkConfig.signals.customEventCollectorUrl)
            assertThat(resolved.customEvent.getUrl()).isNotEqualTo(fallbackCustomEvent.getUrl())
        }

        @Test
        fun `all endpoints carry the provided headers`() {
            val resolvedWithHeaders =
                PulseEndpointUtils.resolve(
                    sdkConfig = sdkConfig,
                    headers = headers,
                    fallbackSpan = fallbackSpan,
                    fallbackLog = fallbackLog,
                    fallbackMetric = fallbackMetric,
                    fallbackCustomEvent = fallbackCustomEvent,
                )
            assertThat(resolvedWithHeaders.span.getHeaders()).containsEntry("X-API-KEY", "project-abc")
            assertThat(resolvedWithHeaders.log.getHeaders()).containsEntry("X-API-KEY", "project-abc")
            assertThat(resolvedWithHeaders.metric.getHeaders()).containsEntry("X-API-KEY", "project-abc")
            assertThat(resolvedWithHeaders.customEvent.getHeaders()).containsEntry("X-API-KEY", "project-abc")
        }
    }
}
