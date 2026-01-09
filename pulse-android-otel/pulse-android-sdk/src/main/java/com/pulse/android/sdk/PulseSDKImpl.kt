@file:OptIn(Incubating::class)
@file:Suppress("unused")

package com.pulse.android.sdk

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.pulse.otel.utils.putAttributesFrom
import com.pulse.otel.utils.toAttributes
import com.pulse.sampling.core.PulseSamplingSignalProcessors
import com.pulse.sampling.core.providers.PulseSdkConfigRestProvider
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseUserAttributes
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.export.FilteringSpanExporter
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import io.opentelemetry.semconv.incubating.UserIncubatingAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.Predicate

internal class PulseSDKImpl :
    PulseSDK,
    CoroutineScope by MainScope() {
    override fun isInitialized(): Boolean = isInitialised

    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    override fun initialize(
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
        if (isInitialized()) {
            return
        }
        this.application = application

        val sharedPrefs =
            application.getSharedPreferences(
                "pulse_sdk_config",
                Context.MODE_PRIVATE,
            )

        val json = Json {}
        val currentSdkConfig =
            sharedPrefs.getString(PULSE_SDK_CONFIG_KEY, null)?.let {
                json.decodeFromString<PulseSdkConfig>(it)
            }
        launch {
            val apiCache = File(application.cacheDir, "pulse${File.separatorChar}apiCache")
            apiCache.mkdirs()
            val newConfig =
                PulseSdkConfigRestProvider(apiCache) {
                    "${endpointBaseUrl.replace(":4318", ":8080")}/v1/configs/active/"
                }.provide() ?: return@launch
            sharedPrefs.edit(commit = true) {
                putString(PULSE_SDK_CONFIG_KEY, Json {}.encodeToString(newConfig))
            }
        }

        pulseSamplingProcessors =
            currentSdkConfig?.let {
                PulseSamplingSignalProcessors(
                    context = application,
                    sdkConfig = currentSdkConfig,
                )
            }
        pulseSpanProcessor = PulseSdkSignalProcessors()
        val config = OtelRumConfig()
        val (internalTracerProviderCustomizer, internalLoggerProviderCustomizer) = createSignalsProcessors(config)
        val mergedTracerProviderCustomizer =
            if (tracerProviderCustomizer != null) {
                BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, app ->
                    val builderWithInternal = internalTracerProviderCustomizer.apply(tracerProviderBuilder, app)
                    tracerProviderCustomizer.apply(builderWithInternal, app)
                }
            } else {
                internalTracerProviderCustomizer
            }

        val mergedLoggerProviderCustomizer =
            if (loggerProviderCustomizer != null) {
                BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, app ->
                    val builderWithInternal = internalLoggerProviderCustomizer.apply(loggerProviderBuilder, app)
                    loggerProviderCustomizer.apply(builderWithInternal, app)
                }
            } else {
                internalLoggerProviderCustomizer
            }

        val finalSpanEndpointConnectivity =
            currentSdkConfig?.let {
                HttpEndpointConnectivity.forTraces(it.signals.spanCollectorUrl)
            } ?: spanEndpointConnectivity
        val finalLogEndpointConnectivity =
            currentSdkConfig?.let {
                HttpEndpointConnectivity.forLogs(it.signals.logsCollectorUrl)
            } ?: logEndpointConnectivity
        val finalMetricEndpointConnectivity =
            currentSdkConfig?.let {
                HttpEndpointConnectivity.forMetrics(it.signals.metricCollectorUrl)
            } ?: metricEndpointConnectivity

        val otlpSpanExporter: SpanExporter =
            OtlpHttpSpanExporter
                .builder()
                .setEndpoint(finalSpanEndpointConnectivity.getUrl())
                .setHeaders(finalSpanEndpointConnectivity::getHeaders)
                .build()

        val attrRejects = mutableMapOf<AttributeKey<*>, Predicate<*>>()
        attrRejects[AttributeKey.booleanKey("pulse.internal")] = Predicate<Boolean> { it == true }
        val filteredSpanExporter =
            FilteringSpanExporter
                .builder(otlpSpanExporter)
                .rejectSpansWithAttributesMatching(attrRejects)
                .build()

        val otlpLogExporter: LogRecordExporter =
            OtlpHttpLogRecordExporter
                .builder()
                .setEndpoint(finalLogEndpointConnectivity.getUrl())
                .setHeaders(finalLogEndpointConnectivity::getHeaders)
                .build()

        val otlMetricExporter: MetricExporter =
            OtlpHttpMetricExporter
                .builder()
                .setEndpoint(finalMetricEndpointConnectivity.getUrl())
                .setHeaders(finalMetricEndpointConnectivity::getHeaders)
                .build()

        val spanExporter: SpanExporter = pulseSamplingProcessors?.SampledSpanExporter(filteredSpanExporter) ?: filteredSpanExporter
        val logExporter: LogRecordExporter = pulseSamplingProcessors?.SampledLogExporter(otlpLogExporter) ?: otlpLogExporter
        val metricExporter: MetricExporter = pulseSamplingProcessors?.SampledMetricExporter(otlMetricExporter) ?: otlMetricExporter

        instrumentations?.let { configure ->
            InstrumentationConfiguration(config).configure()
            pulseSamplingProcessors?.run {
                getDisabledFeatures().forEach {
                    when (it) {
                        PulseFeatureName.JAVA_CRASH -> {
                            config.suppressInstrumentation("crash")
                        }

                        PulseFeatureName.NETWORK_CHANGE -> {
                            config.disableNetworkAttributes()
                        }

                        PulseFeatureName.JAVA_ANR -> {
                            config.suppressInstrumentation("anr")
                        }

                        PulseFeatureName.INTERACTION -> {
                            config.suppressInstrumentation(InteractionInstrumentation.INSTRUMENTATION_NAME)
                        }

                        PulseFeatureName.CPP_CRASH -> {
                            // no-op
                        }

                        PulseFeatureName.CPP_ANR -> {
                            // no-op
                        }

                        PulseFeatureName.UNKNOWN -> {
                            // no-op
                        }
                    }
                }
            }
        }
        otelInstance =
            OpenTelemetryRumInitializer.initialize(
                application = application,
                endpointBaseUrl = endpointBaseUrl,
                endpointHeaders = endpointHeaders,
                // todo make it explicit as to which config should be chosen
                //  1. Either remove this value
                //  2. Or give options like LocalOnly, ConfigOrFallback
                spanEndpointConnectivity = finalSpanEndpointConnectivity,
                logEndpointConnectivity = finalLogEndpointConnectivity,
                metricEndpointConnectivity = finalMetricEndpointConnectivity,
                sessionConfig = sessionConfig,
                globalAttributes =
                    {
                        val attributesBuilder = Attributes.builder()
                        if (userProps.isNotEmpty()) {
                            for ((key, value) in userProps) {
                                attributesBuilder.put(
                                    PulseUserAttributes.PULSE_USER_PARAMETER.getAttributeKey(key),
                                    value.toString(),
                                )
                            }
                        }
                        if (userSessionEmitter.userId != null) {
                            attributesBuilder.put(UserIncubatingAttributes.USER_ID, userSessionEmitter.userId)
                        }
                        attributesBuilder.put(AppIncubatingAttributes.APP_INSTALLATION_ID, installationIdManager.installationId)
                        if (globalAttributes != null) {
                            attributesBuilder.putAll(globalAttributes.invoke())
                        }
                        attributesBuilder.build()
                    },
                diskBuffering = diskBuffering,
                resource = resource,
                rumConfig = config,
                tracerProviderCustomizer = mergedTracerProviderCustomizer,
                loggerProviderCustomizer = mergedLoggerProviderCustomizer,
                spanExporter = spanExporter,
                logRecordExporter = logExporter,
                metricExporter = metricExporter,
            )
        isInitialised = true
    }

    private fun createSignalsProcessors(
        config: OtelRumConfig,
    ): Pair<
        BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>,
        BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>,
    > {
        val tracerProviderCustomizer =
            BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, _ ->
                tracerProviderBuilder.addSpanProcessor(
                    PulseSdkSignalProcessors.PulseSpanTypeAttributesAppender(),
                )
                // interaction specific attributed present in other spans
                if (!config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)) {
                    tracerProviderBuilder.addSpanProcessor(
                        InteractionInstrumentation.createSpanProcessor(
                            AndroidInstrumentationLoader
                                .getInstrumentation(
                                    InteractionInstrumentation::class.java,
                                ).interactionManagerInstance,
                        ),
                    )
                }
                tracerProviderBuilder
            }

        val loggerProviderCustomizer =
            BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, _ ->
                loggerProviderBuilder.addLogRecordProcessor(
                    pulseSpanProcessor.PulseLogTypeAttributesAppender(),
                )
                if (!config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)) {
                    loggerProviderBuilder.addLogRecordProcessor(
                        InteractionInstrumentation.createLogProcessor(
                            AndroidInstrumentationLoader
                                .getInstrumentation(
                                    InteractionInstrumentation::class.java,
                                ).interactionManagerInstance,
                        ),
                    )
                }
                loggerProviderBuilder
            }
        return tracerProviderCustomizer to loggerProviderCustomizer
    }

    override fun setUserId(id: String?) {
        userSessionEmitter.userId = id
    }

    override fun setUserProperty(
        name: String,
        value: Any?,
    ) {
        if (value != null) {
            userProps[name] = value
        } else {
            userProps.remove(name)
        }
    }

    fun setUserProperties(properties: Map<String, Any?>) {
        properties.forEach {
            setUserProperty(it.key, it.value)
        }
    }

    override fun setUserProperties(builderAction: MutableMap<String, Any?>.() -> Unit) {
        setUserProperties(mutableMapOf<String, Any?>().apply(builderAction))
    }

    override fun trackEvent(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        logger
            .logRecordBuilder()
            .apply {
                setObservedTimestamp(observedTimeStampInMs, TimeUnit.MILLISECONDS)
                setBody(name)
                setEventName(CUSTOM_EVENT_NAME)
                setAttribute(
                    PulseAttributes.PULSE_TYPE,
                    PulseAttributes.PulseTypeValues.CUSTOM_EVENT,
                )
                setAllAttributes(params.toAttributes())
                emit()
            }
    }

    override fun trackNonFatal(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        logger
            .logRecordBuilder()
            .apply {
                setObservedTimestamp(observedTimeStampInMs, TimeUnit.MILLISECONDS)
                setBody(name)
                setEventName(CUSTOM_NON_FATAL_EVENT_NAME)
                setAttribute(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.NON_FATAL)
                setAllAttributes(params.toAttributes())
                emit()
            }
    }

    override fun trackNonFatal(
        throwable: Throwable,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        logger
            .logRecordBuilder()
            .apply {
                setObservedTimestamp(observedTimeStampInMs, TimeUnit.MILLISECONDS)
                setBody(throwable.message ?: "Non fatal error of type ${throwable.javaClass.name}")
                val attributesBuilder =
                    Attributes
                        .builder()
                        .put(ExceptionAttributes.EXCEPTION_MESSAGE, throwable.message)
                        .put(
                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                            throwable.stackTraceToString(),
                        ).put(ExceptionAttributes.EXCEPTION_TYPE, throwable.javaClass.name)
                attributesBuilder putAttributesFrom params
                setAllAttributes(attributesBuilder.build())
                setEventName(CUSTOM_NON_FATAL_EVENT_NAME)
                setAttribute(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.NON_FATAL)
                emit()
            }
    }

    override fun <T> trackSpan(
        spanName: String,
        params: Map<String, Any?>,
        action: () -> T,
    ) {
        val span = tracer.spanBuilder(spanName).startSpan()
        try {
            action()
        } finally {
            span.end()
        }
    }

    override fun startSpan(
        spanName: String,
        params: Map<String, Any?>,
    ): () -> Unit {
        val span = tracer.spanBuilder(spanName).startSpan()
        return {
            span.end()
        }
    }

    override fun getOtelOrNull(): OpenTelemetryRum? = otelInstance

    override fun getOtelOrThrow(): OpenTelemetryRum = otelInstance ?: throwSdkNotInitError()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun throwSdkNotInitError(): Nothing {
        error("Pulse SDK is not initialized. Please call PulseSDK.initialize")
    }

    private val logger: Logger by lazy {
        getOtelOrThrow()
            .getOpenTelemetry()
            .logsBridge
            .loggerBuilder(INSTRUMENTATION_SCOPE)
            .build()
    }

    private val tracer: Tracer by lazy {
        getOtelOrThrow()
            .getOpenTelemetry()
            .tracerProvider
            .tracerBuilder(INSTRUMENTATION_SCOPE)
            .build()
    }

    private val sharedPrefsData by lazy {
        val application = application ?: throwSdkNotInitError()
        application.getSharedPreferences(
            "pulse_sdk_data",
            Context.MODE_PRIVATE,
        )
    }

    private val userSessionEmitter: PulseUserSessionEmitter by lazy {
        PulseUserSessionEmitter({ logger }, sharedPrefsData)
    }

    private val installationIdManager: PulseInstallationIdManager by lazy {
        PulseInstallationIdManager(sharedPrefsData) { logger }
    }

    private var isInitialised: Boolean = false

    private lateinit var pulseSpanProcessor: PulseSdkSignalProcessors
    private var pulseSamplingProcessors: PulseSamplingSignalProcessors? = null
    private var otelInstance: OpenTelemetryRum? = null

    private val userProps = ConcurrentHashMap<String, Any>()
    private var application: Application? = null

    internal companion object {
        private const val INSTRUMENTATION_SCOPE = "com.pulse.android.sdk"
        private const val CUSTOM_EVENT_NAME = "pulse.custom_event"
        internal const val CUSTOM_NON_FATAL_EVENT_NAME = "pulse.custom_non_fatal"
        private const val PULSE_SDK_CONFIG_KEY = "sdk_config"
    }
}
