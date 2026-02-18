@file:OptIn(Incubating::class)
@file:Suppress("unused")

package com.pulse.android.sdk

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.pulse.otel.utils.PulseOtelUtils
import com.pulse.otel.utils.putAttributesFrom
import com.pulse.otel.utils.toAttributes
import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.core.exporters.PulseSignalSelectExporter
import com.pulse.sampling.core.providers.PulseSdkConfigRestProvider
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseProp
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseUserAttributes
import io.opentelemetry.android.AndroidResource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.Predicate
import kotlin.system.measureNanoTime

internal class PulseSDKImpl :
    PulseSDK,
    CoroutineScope by MainScope() {
    override fun isInitialized(): Boolean = isInitialised && !isShutdown

    override fun shutdown() {
        if (isShutdown) {
            PulseOtelUtils.logDebug(TAG) { "Shutdown skipped: already shut down" }
            return
        }
        launch(Dispatchers.Main.immediate) {
            if (isShutdown) return@launch
            otelInstance?.shutdown()
            otelInstance = null
            isShutdown = true
            PulseOtelUtils.logDebug(TAG) { "Pulse SDK shut down" }
        }
    }

    override fun initialize(
        application: Application,
        endpointBaseUrl: String,
        projectId: String,
        endpointHeaders: Map<String, String>,
        spanEndpointConnectivity: EndpointConnectivity,
        logEndpointConnectivity: EndpointConnectivity,
        metricEndpointConnectivity: EndpointConnectivity,
        customEventConnectivity: EndpointConnectivity,
        configEndpointUrl: String?,
        resource: (ResourceBuilder.() -> Unit)?,
        sessionConfig: SessionConfig,
        globalAttributes: (() -> Attributes)?,
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
        measureNanoTime {
            initializeInternal(
                application,
                endpointBaseUrl,
                projectId,
                tracerProviderCustomizer,
                loggerProviderCustomizer,
                spanEndpointConnectivity,
                logEndpointConnectivity,
                metricEndpointConnectivity,
                customEventConnectivity,
                configEndpointUrl,
                resource,
                instrumentations,
                endpointHeaders,
                sessionConfig,
                globalAttributes,
                diskBuffering,
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
        projectId: String,
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
        diskBuffering: (DiskBufferingConfigurationSpec.() -> Unit)?,
    ) {
        this.application = application

        val sharedPrefs =
            application.getSharedPreferences(
                "pulse_sdk_config",
                Context.MODE_PRIVATE,
            )

        val json = Json {}
        val currentSdkConfig =
            sharedPrefs.getString(PrefsName.PULSE_SDK_CONFIG_KEY, null)?.let {
                json.decodeFromString<PulseSdkConfig>(it)
            }

        PulseOtelUtils.logDebug(TAG) { "currentSdkConfig config version = ${currentSdkConfig?.version ?: "currentSdkConfig is null"}" }

        // Merge projectId with endpointHeaders for all API calls
        val projectIdHeader = createProjectIdHeader(projectId)
        val endpointHeadersWithProject = endpointHeaders + projectIdHeader

        @Suppress("InjectDispatcher") // we are not exposing this dispatchers to client
        launch(Dispatchers.IO) {
            val apiCache = File(application.cacheDir, "pulse${File.separatorChar}apiCache")
            apiCache.mkdirs()
            val newConfig =
                PulseSdkConfigRestProvider(apiCache, endpointHeadersWithProject) {
                    configEndpointUrl
                        ?: "${PulseOtelUtils.endWithSlash(endpointBaseUrl.replace(":4318", ":8080"))}v1/configs/active/"
                }.provide()
            val isDifferentVersion = newConfig != null && newConfig.version != currentSdkConfig?.version
            PulseOtelUtils.logDebug(TAG) {
                "newConfigVersion = ${newConfig?.version ?: "newConfig is null"}, " +
                    "oldConfigVersion = ${currentSdkConfig?.version ?: "currentSdkConfig is null"}, " +
                    "shouldUpdate = $isDifferentVersion"
            }
            if (isDifferentVersion) {
                sharedPrefs.edit(commit = true) {
                    putString(PrefsName.PULSE_SDK_CONFIG_KEY, Json {}.encodeToString(newConfig))
                }
            }
        }

        // Build resource once to determine currentSdkName for PulseSamplingSignalProcessors
        val resourceBuilder = AndroidResource.createDefault(application).toBuilder()
        resourceBuilder.put(PulseAttributes.TELEMETRY_SDK_NAME_KEY, PulseAttributes.PulseSdkNames.ANDROID_JAVA)
        resource?.invoke(resourceBuilder)
        val builtResource = resourceBuilder.build()
        val currentSdkName =
            PulseSdkName.fromName(
                builtResource.getAttribute(PulseAttributes.TELEMETRY_SDK_NAME_KEY),
            )

        // Set default telemetry.sdk.name for Android Java SDK
        val androidJavaResource: (ResourceBuilder.() -> Unit) = {
            put(PulseAttributes.TELEMETRY_SDK_NAME_KEY, PulseAttributes.PulseSdkNames.ANDROID_JAVA)
            put(PulseAttributes.PROJECT_ID, projectId)
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
                val url = it.signals.spanCollectorUrl
                PulseOtelUtils.logDebug(TAG) { "spanCollectorUrl = $url" }
                HttpEndpointConnectivity(url = url, headers = endpointHeadersWithProject)
            } ?: spanEndpointConnectivity
        val finalLogEndpointConnectivity =
            currentSdkConfig?.let {
                val url = it.signals.logsCollectorUrl
                PulseOtelUtils.logDebug(TAG) { "logsCollectorUrl = $url" }
                HttpEndpointConnectivity(url = url, headers = endpointHeadersWithProject)
            } ?: logEndpointConnectivity
        val finalMetricEndpointConnectivity =
            currentSdkConfig?.let {
                val url = it.signals.metricCollectorUrl
                PulseOtelUtils.logDebug(TAG) { "metricCollectorUrl = $url" }
                HttpEndpointConnectivity(url = url, headers = endpointHeadersWithProject)
            } ?: metricEndpointConnectivity
        val finalCustomEventEndpointConnectivity =
            currentSdkConfig?.let {
                val url = it.signals.customEventCollectorUrl
                PulseOtelUtils.logDebug(TAG) { "customEventCollectorUrl = $url" }
                HttpEndpointConnectivity(url = url, headers = endpointHeadersWithProject)
            } ?: customEventConnectivity

        val otlpSpanExporter: SpanExporter =
            OtlpHttpSpanExporter
                .builder()
                .setEndpoint(finalSpanEndpointConnectivity.getUrl())
                .setHeaders { finalSpanEndpointConnectivity.getHeaders() + projectIdHeader }
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
                            .setHeaders { finalLogEndpointConnectivity.getHeaders() + projectIdHeader }
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
                            .setHeaders { finalCustomEventEndpointConnectivity.getHeaders() + projectIdHeader }
                            .build(),
                ),
            )

        val otlMetricExporter: MetricExporter =
            OtlpHttpMetricExporter
                .builder()
                .setEndpoint(finalMetricEndpointConnectivity.getUrl())
                .setHeaders { finalMetricEndpointConnectivity.getHeaders() + projectIdHeader }
                .build()

        val spanExporter: SpanExporter = pulseSamplingProcessors?.SampledSpanExporter(filteredSpanExporter) ?: filteredSpanExporter
        val logExporter: LogRecordExporter = pulseSamplingProcessors?.SampledLogExporter(otlpLogExporter) ?: otlpLogExporter
        val metricExporter: MetricExporter = pulseSamplingProcessors?.SampledMetricExporter(otlMetricExporter) ?: otlMetricExporter

        instrumentations?.let { configure ->
            val instrumentationConfig = InstrumentationConfiguration(config, endpointHeadersWithProject)
            instrumentationConfig.configure()
            if (currentSdkConfig != null) {
                instrumentationConfig.interaction { setConfigUrl { PulseOtelUtils.endWithSlash(currentSdkConfig.interaction.configUrl) } }
            }
            pulseSamplingProcessors?.run {
                val enabledFeatures = getEnabledFeatures()
                enumValues<PulseFeatureName>().forEach { feature ->
                    if (feature !in enabledFeatures) {
                        PulseOtelUtils.logDebug(TAG) { "Disabling feature = $feature" }
                        when (feature) {
                            PulseFeatureName.JAVA_CRASH -> {
                                config.suppressInstrumentation("crash")
                            }

                            PulseFeatureName.JS_CRASH -> {
                                // no-op
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

                            PulseFeatureName.NETWORK_INSTRUMENTATION -> {
                                // no-op
                            }

                            PulseFeatureName.SCREEN_SESSION -> {
                                // no-op
                            }

                            PulseFeatureName.CUSTOM_EVENTS -> {
                                isCustomEventEnabled = false
                            }

                            PulseFeatureName.RN_SCREEN_LOAD -> {
                                // no-op
                            }

                            PulseFeatureName.RN_SCREEN_INTERACTIVE -> {
                                // no-op
                            }

                            PulseFeatureName.UNKNOWN -> {
                                // no-op
                            }
                        }
                    }
                }
            }
        }

        otelInstance =
            OpenTelemetryRumInitializer.initialize(
                application = application,
                endpointBaseUrl = endpointBaseUrl,
                endpointHeaders = endpointHeadersWithProject,
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

    override fun setUserId(id: String?) {
        if (isShutdown) return
        userSessionEmitter.userId = id
    }

    override fun setUserProperty(
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

    fun setUserProperties(properties: Map<String, Any?>) {
        properties.forEach {
            setUserProperty(it.key, it.value)
        }
    }

    override fun setUserProperties(builderAction: MutableMap<String, Any?>.() -> Unit) {
        if (isShutdown) return
        setUserProperties(mutableMapOf<String, Any?>().apply(builderAction))
    }

    override fun trackEvent(
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

    override fun trackNonFatal(
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

    override fun trackNonFatal(
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

    override fun <T> trackSpan(
        spanName: String,
        params: Map<String, Any?>,
        action: () -> T,
    ) {
        if (isShutdown) {
            action()
            return
        }
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
        if (isShutdown) return {}
        val span = tracer.spanBuilder(spanName).startSpan()
        return {
            span.end()
        }
    }

    override fun getOtelOrNull(): OpenTelemetryRum? = if (isShutdown) null else otelInstance

    override fun getOtelOrThrow(): OpenTelemetryRum {
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

    internal companion object {
        private const val INSTRUMENTATION_SCOPE = "com.pulse.android.sdk"
        private const val CUSTOM_EVENT_NAME = "pulse.custom_event"
        internal const val CUSTOM_NON_FATAL_EVENT_NAME = "pulse.custom_non_fatal"
        private const val TAG = "AndroidSDK"
        private const val PROJECT_ID_HEADER_KEY = "X-API-KEY"

        internal object PrefsName {
            internal const val LOCATION_PREF_FILE_NAME = "pulse_location_data"
            internal const val PULSE_SDK_CONFIG_KEY = "sdk_config"
        }

        internal fun createProjectIdHeader(projectId: String): Map<String, String> = mapOf(PROJECT_ID_HEADER_KEY to projectId)
    }
}
