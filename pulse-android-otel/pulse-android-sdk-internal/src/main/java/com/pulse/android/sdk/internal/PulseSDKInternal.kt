@file:OptIn(Incubating::class)
@file:Suppress("unused")

package com.pulse.android.sdk.internal

import android.app.Application
import android.content.Context
import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.PulseDataCollectionConsent
import com.pulse.android.sdk.internal.beforesend.PulseBeforeSendLogExporter
import com.pulse.android.sdk.internal.beforesend.PulseBeforeSendMetricExporter
import com.pulse.android.sdk.internal.beforesend.PulseBeforeSendSpanExporter
import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.core.exporters.PulseSignalSelectExporter
import com.pulse.sampling.models.PulseProp
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseSessionAttributes
import com.pulse.semconv.PulseUserAttributes
import com.pulse.utils.PulseOtelUtils
import com.pulse.utils.putAttributesFrom
import com.pulse.utils.toAttributes
import io.opentelemetry.android.AndroidResource
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.export.FilteringSpanExporter
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation
import io.opentelemetry.android.instrumentation.location.processors.LocationAttributesLogRecordAppender
import io.opentelemetry.android.instrumentation.location.processors.LocationAttributesSpanAppender
import io.opentelemetry.android.instrumentation.location.processors.LocationInstrumentationConstants
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
import io.opentelemetry.sdk.resources.ResourceBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import io.opentelemetry.semconv.incubating.UserIncubatingAttributes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.Predicate
import kotlin.system.measureNanoTime

/**
 * Internal PulseSDK implementation. This is internal module so API compatibility and behaviour is not guaranteed.
 * Provides initialization with tracer and logger provider customizers for React Native
 * and other integrations that need to add custom processors.
 */
@Suppress("LargeClass")
public class PulseSDKInternal : CoroutineScope by MainScope() {
    public fun isInitialized(): Boolean = isInitialised && !isShutdown

    /**
     * Initialize the Pulse SDK with optional tracer and logger provider customizers.
     * Used by React Native to add RN-specific screen attribute processors.
     */
    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    public fun initialize(
        application: Application,
        endpointBaseUrl: String,
        apiKey: String,
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
        beforeSendData: PulseBeforeSendData? = null,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)?,
        tracerProviderCustomizer: BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>?,
        loggerProviderCustomizer: BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>?,
        instrumentations: (InstrumentationConfiguration.() -> Unit)?,
    ) {
        if (isShutdown) {
            PulseOtelUtils.logDebug(TAG) { "Initialisation skipped: SDK has been shut down" }
            return
        }
        if (isInitialized()) {
            PulseOtelUtils.logDebug(TAG) { "Initialisation skipped already initialised" }
            return
        }
        this.application = application
        measureNanoTime {
            @Suppress("InjectDispatcher") // we are not exposing this dispatchers to client
            initializeInternal(
                application = application,
                endpointBaseUrl = endpointBaseUrl,
                apiKey = apiKey,
                dataCollectionState = dataCollectionState,
                tracerProviderCustomizer = tracerProviderCustomizer,
                loggerProviderCustomizer = loggerProviderCustomizer,
                spanEndpointConnectivity = spanEndpointConnectivity,
                logEndpointConnectivity = logEndpointConnectivity,
                metricEndpointConnectivity = metricEndpointConnectivity,
                customEventConnectivity = customEventConnectivity,
                configEndpointUrl = configEndpointUrl,
                resource = resource,
                instrumentations = instrumentations,
                endpointHeaders = endpointHeaders,
                sessionConfig = sessionConfig,
                globalAttributes = globalAttributes,
                diskBuffering = diskBuffering,
                ioDispatcher = Dispatchers.IO,
                beforeSendData = beforeSendData,
            )
        }.also {
            PulseOtelUtils.logDebug(TAG) { "Initialisation succeeded in $it ns" }
        }
        isInitialised = true
    }

    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    private fun initializeInternal(
        application: Application,
        endpointBaseUrl: String,
        apiKey: String,
        dataCollectionState: PulseDataCollectionConsent,
        tracerProviderCustomizer: BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>?,
        loggerProviderCustomizer: BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>?,
        spanEndpointConnectivity: EndpointConnectivity,
        logEndpointConnectivity: EndpointConnectivity,
        metricEndpointConnectivity: EndpointConnectivity,
        customEventConnectivity: EndpointConnectivity,
        configEndpointUrl: String?,
        resource: (ResourceBuilder.() -> Unit)?,
        instrumentations: (InstrumentationConfiguration.() -> Unit)?,
        endpointHeaders: Map<String, String>,
        sessionConfig: SessionConfig,
        globalAttributes: (() -> Attributes)?,
        beforeSendData: PulseBeforeSendData? = null,
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)?,
        ioDispatcher: CoroutineDispatcher,
    ) {
        if (dataCollectionState == PulseDataCollectionConsent.DENIED) {
            oldState = PulseDataCollectionConsent.DENIED
            PulseOtelUtils.logDebug(TAG) { "initializeInternal returned early as started with DENIED consent" }
            return
        }
        val sharedPrefs =
            application.getSharedPreferences(
                "pulse_sdk_config",
                Context.MODE_PRIVATE,
            )

        val apiKeyHeader = createApiKeyHeader(apiKey)
        val endpointHeadersWithProject = endpointHeaders + apiKeyHeader

        val currentSdkConfig =
            PulseSdkConfigRefresher.loadAndRefresh(
                cacheDir = application.cacheDir,
                configUrl = PulseSdkConfigRefresher.resolveConfigUrl(configEndpointUrl, endpointBaseUrl),
                headers = endpointHeadersWithProject,
                sharedPrefs = sharedPrefs,
                prefsKey = PrefsName.PULSE_SDK_CONFIG_KEY,
                scope = this,
                ioDispatcher = ioDispatcher,
            )

        val resourceBuilder = AndroidResource.createDefault(application).toBuilder()
        resourceBuilder.put(PulseAttributes.TELEMETRY_SDK_NAME_KEY, PulseAttributes.PulseSdkNames.ANDROID_JAVA)
        resource?.invoke(resourceBuilder)
        val builtResource = resourceBuilder.build()
        val currentSdkName =
            PulseSdkName.fromName(
                builtResource.getAttribute(PulseAttributes.TELEMETRY_SDK_NAME_KEY),
            )

        val androidJavaResource: (ResourceBuilder.() -> Unit) = {
            put(PulseAttributes.TELEMETRY_SDK_NAME_KEY, PulseAttributes.PulseSdkNames.ANDROID_JAVA)
            put(PulseAttributes.PROJECT_ID, extractProjectID(apiKey))
            resource?.invoke(this)
        }

        pulseSamplingProcessors =
            currentSdkConfig?.let {
                PulseSamplingSignalProcessors(
                    context = application,
                    sdkConfig = currentSdkConfig,
                    currentSdkName = currentSdkName,
                )
            }
        pulseSpanProcessor = PulseSdkSignalProcessors()
        val config = OtelRumConfig()
        val meteredSessionManager = OpenTelemetryRumInitializer.createMeteredSessionManager(application)
        val meteringSessionHeader = createMeteringSessionHeader(meteredSessionManager.getSessionId())
        val (internalTracerProviderCustomizer, internalLoggerProviderCustomizer) = createSignalsProcessors(config)
        val mergedTracerProviderCustomizer =
            PulseCustomizerUtils.mergeTracerCustomizers(
                internal = internalTracerProviderCustomizer,
                external = tracerProviderCustomizer,
            )
        val mergedLoggerProviderCustomizer =
            PulseCustomizerUtils.mergeLoggerCustomizers(
                internal = internalLoggerProviderCustomizer,
                external = loggerProviderCustomizer,
            )

        val resolvedEndpoints =
            PulseEndpointUtils.resolve(
                sdkConfig = currentSdkConfig,
                headers = endpointHeadersWithProject,
                fallbackSpan = spanEndpointConnectivity,
                fallbackLog = logEndpointConnectivity,
                fallbackMetric = metricEndpointConnectivity,
                fallbackCustomEvent = customEventConnectivity,
            )
        val finalSpanEndpointConnectivity = resolvedEndpoints.span
        val finalLogEndpointConnectivity = resolvedEndpoints.log
        val finalMetricEndpointConnectivity = resolvedEndpoints.metric
        val finalCustomEventEndpointConnectivity = resolvedEndpoints.customEvent

        val otlpSpanExporter: SpanExporter =
            OtlpHttpSpanExporter
                .builder()
                .setEndpoint(finalSpanEndpointConnectivity.getUrl())
                .setHeaders { finalSpanEndpointConnectivity.getHeaders() + apiKeyHeader + meteringSessionHeader }
                .build()

        val attrRejects = mutableMapOf<AttributeKey<*>, Predicate<*>>()
        attrRejects[AttributeKey.booleanKey("pulse.internal")] = Predicate<Boolean> { it == true }
        val filteredSpanExporter =
            FilteringSpanExporter
                .builder(otlpSpanExporter)
                .rejectSpansWithAttributesMatching(attrRejects)
                .build()

        val otlpLogExporter: LogRecordExporter =
            PulseSignalSelectExporter(currentSdkName).SelectedLogExporter(
                listOf(
                    PulseSignalMatchCondition.allMatchLogCondition to
                        OtlpHttpLogRecordExporter
                            .builder()
                            .setEndpoint(finalLogEndpointConnectivity.getUrl())
                            .setHeaders { finalLogEndpointConnectivity.getHeaders() + apiKeyHeader + meteringSessionHeader }
                            .build(),
                    PulseSignalMatchCondition(
                        name = ".*",
                        props =
                            setOf(
                                PulseProp(name = PulseAttributes.PULSE_TYPE.key, value = PulseAttributes.PulseTypeValues.CUSTOM_EVENT),
                            ),
                        scopes = PulseSignalScope.allValuesExceptUnknown,
                        sdks = PulseSdkName.allValuesExceptUnknown,
                    ) to
                        OtlpHttpLogRecordExporter
                            .builder()
                            .setEndpoint(finalCustomEventEndpointConnectivity.getUrl())
                            .setHeaders { finalCustomEventEndpointConnectivity.getHeaders() + apiKeyHeader + meteringSessionHeader }
                            .build(),
                ),
            )

        val otlMetricExporter: MetricExporter =
            OtlpHttpMetricExporter
                .builder()
                .setEndpoint(finalMetricEndpointConnectivity.getUrl())
                .setHeaders { finalMetricEndpointConnectivity.getHeaders() + apiKeyHeader + meteringSessionHeader }
                .build()

        val baseSpanExporter: SpanExporter = pulseSamplingProcessors?.SampledSpanExporter(filteredSpanExporter) ?: filteredSpanExporter
        val baseLogExporter: LogRecordExporter = pulseSamplingProcessors?.SampledLogExporter(otlpLogExporter) ?: otlpLogExporter
        val baseMetricExporter: MetricExporter = pulseSamplingProcessors?.SampledMetricExporter(otlMetricExporter) ?: otlMetricExporter

        val spanExporter: SpanExporter =
            beforeSendData?.let { PulseBeforeSendSpanExporter(it, baseSpanExporter) } ?: baseSpanExporter
        val logExporter: LogRecordExporter =
            beforeSendData?.let { PulseBeforeSendLogExporter(it, baseLogExporter) } ?: baseLogExporter
        val metricExporter: MetricExporter =
            beforeSendData?.let { PulseBeforeSendMetricExporter(it, baseMetricExporter) } ?: baseMetricExporter

        instrumentations?.let { configure ->
            val instrumentationConfig = InstrumentationConfiguration(config, endpointHeadersWithProject)
            instrumentationConfig.configure()
            currentSdkConfig?.interaction?.configUrl?.let { interactionConfigUrl ->
                instrumentationConfig.interaction { setConfigUrl { interactionConfigUrl } }
            }
            pulseSamplingProcessors?.run {
                PulseOtelUtils.logDebug(TAG) { "Applying feature flags" }
                val flagResult = PulseFeatureFlagUtils.apply(config, this)
                isCustomEventEnabled = flagResult.isCustomEventEnabled
            }
        }
        otelInstance =
            OpenTelemetryRumInitializer.initialize(
                application = application,
                endpointBaseUrl = endpointBaseUrl,
                shouldStartSendingData = dataCollectionState == PulseDataCollectionConsent.ALLOWED,
                endpointHeaders = endpointHeadersWithProject,
                // todo make it explicit as to which config should be chosen
                //  1. Either remove this value
                //  2. Or give options like LocalOnly, ConfigOrFallback
                spanEndpointConnectivity = finalSpanEndpointConnectivity,
                logEndpointConnectivity = finalLogEndpointConnectivity,
                metricEndpointConnectivity = finalMetricEndpointConnectivity,
                sessionConfig = sessionConfig,
                meteredSessionProvider = meteredSessionManager,
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
                        attributesBuilder.put(PulseSessionAttributes.PULSE_METERING_SESSION_ID, meteredSessionManager.getSessionId())
                        if (globalAttributes != null) {
                            attributesBuilder.putAll(globalAttributes.invoke())
                        }
                        attributesBuilder.build()
                    },
                resource = androidJavaResource,
                diskBuffering = diskBuffering,
                rumConfig = config,
                tracerProviderCustomizer = mergedTracerProviderCustomizer,
                loggerProviderCustomizer = mergedLoggerProviderCustomizer,
                spanExporter = spanExporter,
                logRecordExporter = logExporter,
                metricExporter = metricExporter,
            )
    }

    private fun createSignalsProcessors(
        config: OtelRumConfig,
    ): Pair<
        BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>,
        BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>,
        // @formatter:off
    > {
        // @formatter:on
        val shouldAddLocationProcessor =
            AndroidInstrumentationLoader
                .get()
                .getByName<AndroidInstrumentation>(LocationInstrumentationConstants.INSTRUMENTATION_NAME) != null &&
                !config.isSuppressed(LocationInstrumentationConstants.INSTRUMENTATION_NAME)
        val tracerProviderCustomizer =
            BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, app ->
                tracerProviderBuilder.addSpanProcessor(
                    PulseSdkSignalProcessors.PulseSpanTypeAttributesAppender(),
                )
                // interaction specific attributes to be attached to other spans
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
                // location attributes
                if (shouldAddLocationProcessor) {
                    val sharedPreferences =
                        app.getSharedPreferences(
                            PrefsName.LOCATION_PREF_FILE_NAME,
                            Context.MODE_PRIVATE,
                        )
                    tracerProviderBuilder.addSpanProcessor(
                        LocationAttributesSpanAppender.create(sharedPreferences),
                    )
                }
                tracerProviderBuilder
            }

        val loggerProviderCustomizer =
            BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, app ->
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
                // location attributes
                if (shouldAddLocationProcessor) {
                    val sharedPreferences =
                        app.getSharedPreferences(
                            PrefsName.LOCATION_PREF_FILE_NAME,
                            Context.MODE_PRIVATE,
                        )
                    loggerProviderBuilder.addLogRecordProcessor(
                        LocationAttributesLogRecordAppender.create(sharedPreferences),
                    )
                }
                loggerProviderBuilder
            }
        return tracerProviderCustomizer to loggerProviderCustomizer
    }

    public fun setUserId(id: String?) {
        if (isShutdown) return
        userSessionEmitter.userId = id
    }

    public fun setUserProperty(
        name: String,
        value: Any?,
    ) {
        if (isShutdown) return
        if (value != null) {
            userProps[name] = value
        } else {
            userProps.remove(name)
        }
    }

    public fun setUserProperties(properties: Map<String, Any?>) {
        properties.forEach {
            setUserProperty(it.key, it.value)
        }
    }

    public fun setUserProperties(builderAction: MutableMap<String, Any?>.() -> Unit) {
        if (isShutdown) return
        setUserProperties(mutableMapOf<String, Any?>().apply(builderAction))
    }

    public fun trackEvent(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        if (isShutdown) return
        if (isCustomEventEnabled) {
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
    }

    public fun trackNonFatal(
        name: String,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        if (isShutdown) return
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

    public fun trackNonFatal(
        throwable: Throwable,
        observedTimeStampInMs: Long,
        params: Map<String, Any?>,
    ) {
        if (isShutdown) return
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

    public fun <T> trackSpan(
        spanName: String,
        params: Map<String, Any?>,
        action: () -> T,
    ) {
        if (isShutdown) {
            action()
            return
        }
        val span =
            tracer
                .spanBuilder(spanName)
                .setAllAttributes(params.toAttributes())
                .startSpan()
        try {
            action()
        } finally {
            span.end()
        }
    }

    public fun startSpan(
        spanName: String,
        params: Map<String, Any?>,
    ): () -> Unit {
        if (isShutdown) return {}
        val span =
            tracer
                .spanBuilder(spanName)
                .setAllAttributes(params.toAttributes())
                .startSpan()
        return {
            span.end()
        }
    }

    public fun shutdown() {
        if (isShutdown) {
            PulseOtelUtils.logDebug(TAG) { "Shutdown skipped: already shut down" }
            return
        }
        launch(Dispatchers.Main.immediate) {
            if (isShutdown) {
                PulseOtelUtils.logDebug(TAG) { "Shutdown skipped: already shut down in main thread" }
                return@launch
            }
            OpenTelemetryRumInitializer.disposeExporters()
            otelInstance?.shutdown()
            otelInstance = null
            isShutdown = true
            PulseOtelUtils.logDebug(TAG) { "Pulse SDK shut down" }
        }
    }

    public fun setDataCollectionState(newState: PulseDataCollectionConsent) {
        if (oldState == PulseDataCollectionConsent.DENIED) {
            PulseOtelUtils.logDebug(TAG) { "setDataCollectionState skipped: SDK has been denied" }
            return
        }
        if (isShutdown) {
            PulseOtelUtils.logDebug(TAG) { "setDataCollectionState skipped: SDK has been shut down" }
            return
        }
        if (newState == oldState) {
            PulseOtelUtils.logDebug(TAG) {
                "setDataCollectionState skipped: oldState = ${oldState ?: "null"} is equal to newState = $newState"
            }
            return
        }

        when (newState) {
            PulseDataCollectionConsent.PENDING -> {
                // no-op
            }

            PulseDataCollectionConsent.DENIED -> {
                shutdown()
                oldState = newState
            }

            PulseDataCollectionConsent.ALLOWED -> {
                OpenTelemetryRumInitializer.setupExporters()
                oldState = newState
            }
        }
    }

    public fun getOtelOrNull(): OpenTelemetryRum? = if (isShutdown) null else otelInstance

    public fun getOtelOrThrow(): OpenTelemetryRum {
        if (isShutdown) throwShutdownError()
        return otelInstance ?: throwSdkNotInitError()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun throwSdkNotInitError(): Nothing {
        error("Pulse SDK is not initialized. Please call PulseSDK.initialize")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun throwShutdownError(): Nothing {
        error("Pulse SDK has been shut down. No further API calls are allowed.")
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
    private var isShutdown: Boolean = false

    private lateinit var pulseSpanProcessor: PulseSdkSignalProcessors
    private var pulseSamplingProcessors: PulseSamplingSignalProcessors? = null
    private var isCustomEventEnabled = true
    private var otelInstance: OpenTelemetryRum? = null

    private val userProps = ConcurrentHashMap<String, Any>()
    private var application: Application? = null
    private var oldState: PulseDataCollectionConsent? = null

    internal companion object {
        private const val INSTRUMENTATION_SCOPE = "com.pulse.android.sdk"
        private const val CUSTOM_EVENT_NAME = "pulse.custom_event"
        internal const val CUSTOM_NON_FATAL_EVENT_NAME = "pulse.custom_non_fatal"
        private const val TAG = "AndroidSDK"
        private const val API_KEY_HEADER = "X-API-KEY"
        private const val METERING_SESSION_HEADER_KEY = "X-Pulse-Metering-Session-ID"

        internal object PrefsName {
            internal const val LOCATION_PREF_FILE_NAME = "pulse_location_data"
            internal const val PULSE_SDK_CONFIG_KEY = "sdk_config"
        }

        internal fun extractProjectID(apiKey: String): String {
            val lastUnderscoreIndex = apiKey.lastIndexOf('_')
            return if (lastUnderscoreIndex > 0) {
                apiKey.take(lastUnderscoreIndex)
            } else {
                apiKey
            }
        }

        private fun createApiKeyHeader(apiKey: String): Map<String, String> = mapOf(API_KEY_HEADER to apiKey)

        private fun createMeteringSessionHeader(meteringSessionId: String): Map<String, String> =
            mapOf(METERING_SESSION_HEADER_KEY to meteringSessionId)
    }
}
