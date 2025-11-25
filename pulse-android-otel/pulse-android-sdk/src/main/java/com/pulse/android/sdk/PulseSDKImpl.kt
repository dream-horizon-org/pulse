@file:OptIn(Incubating::class)
@file:Suppress("unused")

package com.pulse.android.sdk

import android.app.Application
import com.pulse.otel.utils.putAttributesFrom
import com.pulse.otel.utils.toAttributes
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseUserAttributes
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.dsl.DiskBufferingConfigurationSpec
import io.opentelemetry.android.agent.dsl.instrumentation.InstrumentationConfiguration
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.interaction.library.InteractionAttributesSpanAppender
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.UserIncubatingAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

internal class PulseSDKImpl : PulseSDK {
    override fun isInitialized(): Boolean = isInitialised

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
        instrumentations: (InstrumentationConfiguration.() -> Unit)?,
    ) {
        if (isInitialized()) {
            return
        }
        pulseSpanProcessor = PulseSignalProcessor()
        val config = OtelRumConfig()
        val tracerProviderCustomizer =
            BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { tracerProviderBuilder, app ->
                tracerProviderBuilder.addSpanProcessor(
                    pulseSpanProcessor.PulseSpanTypeAttributesAppender()
                )
                // interaction specific attributed present in other spans
                if (config.isInteractionsEnabled) {
                    tracerProviderBuilder.addSpanProcessor(
                        InteractionAttributesSpanAppender.createSpanProcessor()
                    )
                }
                tracerProviderBuilder
            }

        val loggerProviderCustomizer =
            BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { loggerProviderBuilder, app ->
                loggerProviderBuilder.addLogRecordProcessor(
                    pulseSpanProcessor.PulseLogTypeAttributesAppender()
                )
                if (config.isInteractionsEnabled) {
                    loggerProviderBuilder.addLogRecordProcessor(
                        InteractionAttributesSpanAppender.createLogProcessor()
                    )
                }
                loggerProviderBuilder
            }

        otelInstance = OpenTelemetryRumInitializer.initialize(
            application = application,
            endpointBaseUrl = endpointBaseUrl,
            endpointHeaders = endpointHeaders,
            spanEndpointConnectivity = spanEndpointConnectivity,
            logEndpointConnectivity = logEndpointConnectivity,
            metricEndpointConnectivity = metricEndpointConnectivity,
            sessionConfig = sessionConfig,
            globalAttributes = {
                val attributesBuilder = Attributes.builder()
                if (userProps.isNotEmpty()) {
                    for ((key, value) in userProps) {
                        attributesBuilder.put(
                            PulseUserAttributes.PULSE_USER_PARAMETER.getAttributeKey(key),
                            value.toString()
                        )
                    }
                }
                if (userId != null) {
                    attributesBuilder.put(UserIncubatingAttributes.USER_ID, userId)
                }
                if (globalAttributes != null) {
                    attributesBuilder.putAll(globalAttributes.invoke())
                }
                attributesBuilder.build()
            },
            diskBuffering = diskBuffering,
            instrumentations = instrumentations,
            rumConfig = config,
            tracerProviderCustomizer = tracerProviderCustomizer,
            loggerProviderCustomizer = loggerProviderCustomizer,
        )
        isInitialised = true
    }

    override fun setUserId(id: String?) {
        userId = id
    }

    override fun setUserProperty(name: String, value: Any?) {
        userProps[name] = value
    }

    fun setUserProperties(properties: Map<String, Any?>) {
        userProps.putAll(properties)
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
                setEventName("pulse.custom_event")
                setAttribute(
                    PulseAttributes.PULSE_TYPE,
                    PulseAttributes.PulseTypeValues.CUSTOM_EVENT
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
                setEventName("pulse.custom_non_fatal")
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
                setEventName("pulse.custom_non_fatal")
                setAttribute(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.NON_FATAL)
                emit()
            }
    }

    override fun <T> trackSpan(
        spanName: String,
        params: Map<String, Any?>,
        action: () -> T
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
    override fun getOtelOrThrow(): OpenTelemetryRum =
        otelInstance ?: error("Pulse SDK is not initialized. Please call PulseSDK.initialize")

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

    private var isInitialised: Boolean = false

    private lateinit var pulseSpanProcessor: PulseSignalProcessor
    private var otelInstance: OpenTelemetryRum? = null

    private var userId: String? = null
    private var userProps = ConcurrentHashMap<String, Any?>()

    companion object {
        private const val INSTRUMENTATION_SCOPE = "com.pulse.android.sdk"
    }
}