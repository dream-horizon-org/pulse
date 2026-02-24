/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent

import android.app.Application
import io.opentelemetry.android.AndroidResource
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.agent.session.SessionIdTimeoutHandler
import io.opentelemetry.android.agent.session.SessionManager
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.android.internal.services.Services
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.resources.ResourceBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.function.BiFunction
import kotlin.time.Duration.Companion.minutes

@OptIn(Incubating::class)
object OpenTelemetryRumInitializer {
    /**
     * Opinionated [OpenTelemetryRum] initialization.
     *
     * @param application Your android app's application object.
     * @param endpointBaseUrl The base endpoint for exporting all your signals.
     * @param endpointHeaders These will be added to each signal export request.
     * @param spanEndpointConnectivity Span-specific endpoint configuration.
     * @param logEndpointConnectivity Log-specific endpoint configuration.
     * @param metricEndpointConnectivity Metric-specific endpoint configuration.
     * @param sessionConfig The session configuration, which includes inactivity timeout and maximum lifetime durations.
     * @param globalAttributes Configures the set of global attributes to emit with every span and event.
     * @param diskBuffering Configures the disk buffering feature.
     * @param resource Configures the resource attributes that are used globally by acting on a [ResourceBuilder].
     * @param instrumentations Configurations for all the default instrumentations.
     */
    @Suppress("LongParameterList")
    @JvmStatic
    fun initialize(
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
        resource: (ResourceBuilder.() -> Unit)? = null,
        sessionConfig: SessionConfig = SessionConfig.withDefaults(),
        meteredSessionProvider: SessionProvider? = null,
        globalAttributes: (() -> Attributes)? = null,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)? = null,
        tracerProviderCustomizer: BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>? = null,
        meterProviderCustomizer: BiFunction<SdkMeterProviderBuilder, Application, SdkMeterProviderBuilder>? = null,
        loggerProviderCustomizer: BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>? = null,
        spanExporter: SpanExporter =
            OtlpHttpSpanExporter
                .builder()
                .setEndpoint(spanEndpointConnectivity.getUrl())
                .setHeaders(spanEndpointConnectivity::getHeaders)
                .build(),
        logRecordExporter: LogRecordExporter =
            OtlpHttpLogRecordExporter
                .builder()
                .setEndpoint(logEndpointConnectivity.getUrl())
                .setHeaders(logEndpointConnectivity::getHeaders)
                .build(),
        metricExporter: MetricExporter =
            OtlpHttpMetricExporter
                .builder()
                .setEndpoint(metricEndpointConnectivity.getUrl())
                .setHeaders(metricEndpointConnectivity::getHeaders)
                .build(),
        rumConfig: OtelRumConfig = OtelRumConfig(),
    ): OpenTelemetryRum {
        val diskBufferingConfigurationSpec = DiskBufferingConfigurationSpec()
        diskBuffering?.invoke(diskBufferingConfigurationSpec)
        rumConfig.setDiskBufferingConfig(DiskBufferingConfig.create(enabled = diskBufferingConfigurationSpec.isEnabled))

        globalAttributes?.let {
            rumConfig.setGlobalAttributes(it::invoke)
        }

        // Build resource with default Android resource and user customization
        val resourceBuilder = AndroidResource.createDefault(application).toBuilder()
        resource?.invoke(resourceBuilder)
        val finalResource = resourceBuilder.build()

        return OpenTelemetryRum
            .builder(application, rumConfig)
            .apply {
                setResource(finalResource)
                setSessionProvider(createSessionProvider(application, sessionConfig))
                meteredSessionProvider?.let { setMeteredSessionProvider(it) }
                addSpanExporterCustomizer { spanExporter }
                addLogRecordExporterCustomizer { logRecordExporter }
                addMetricExporterCustomizer { metricExporter }
                if (tracerProviderCustomizer != null) addTracerProviderCustomizer(tracerProviderCustomizer)
                if (meterProviderCustomizer != null) addMeterProviderCustomizer(meterProviderCustomizer)
                if (loggerProviderCustomizer != null) addLoggerProviderCustomizer(loggerProviderCustomizer)
            }.build()
    }

    private fun createSessionProvider(
        application: Application,
        sessionConfig: SessionConfig,
    ): SessionProvider {
        val timeoutHandler: SessionIdTimeoutHandler? =
            sessionConfig.backgroundInactivityTimeout?.let {
                val handler = SessionIdTimeoutHandler(Clock.getDefault(), it)
                Services.get(application).appLifecycle.registerListener(handler)
                handler
            }

        return SessionManager.create(application, timeoutHandler, sessionConfig)
    }

    @OptIn(Incubating::class)
    @JvmStatic
    fun createMeteredSessionManager(application: Application): SessionProvider {
        val meteredSessionConfig =
            SessionConfig(
                backgroundInactivityTimeout = null,
                maxLifetime = 30.minutes,
                shouldPersist = true,
            )
        return SessionManager.create(
            application = application,
            timeoutHandler = null,
            sessionConfig = meteredSessionConfig,
            storageKey = "pulse_metered_session_storage",
        )
    }
}
