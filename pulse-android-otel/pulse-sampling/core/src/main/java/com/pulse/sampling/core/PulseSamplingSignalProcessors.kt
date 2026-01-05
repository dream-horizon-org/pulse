package com.pulse.sampling.core

import android.content.Context
import com.pulse.otel.utils.matchesFromRegexCache
import com.pulse.otel.utils.toMap
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import io.opentelemetry.android.export.ModifiedSpanData
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.Value
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.security.SecureRandom
import java.util.Random
import kotlin.experimental.ExperimentalTypeInference

public class PulseSamplingSignalProcessors internal constructor(
    private val context: Context,
    private val sdkConfig: PulseSdkConfig,
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
    private val sessionParser: PulseSessionParser = PulseSessionConfigParser(),
    private val randomIdGenerator: Random = SecureRandom(),
) {
    private fun getDroppedAttributesConfig(scope: PulseSignalScope): List<PulseSignalMatchCondition> =
        sdkConfig
            .signals
            .attributesToDrop
            .filter { it.scopes.contains(scope) && PulseSdkName.CURRENT_SDK_NAME in it.sdks }

    private val shouldSampleThisSession by lazy {
        val samplingRate = sessionParser.parses(context, sdkConfig.sampling)
        val localRandomValue = randomIdGenerator.nextFloat()
        localRandomValue <= samplingRate
    }

    public inner class SampledSpanExporter(
        private val delegateExporter: SpanExporter,
    ) : SpanExporter by delegateExporter {
        private val attributesToDrop by lazy {
            getDroppedAttributesConfig(PulseSignalScope.TRACES)
        }

        override fun export(spans: Collection<SpanData>): CompletableResultCode =
            sampleSession {
                val filteredSpans =
                    spans
                        .asSequence()
                        .filter { spanData ->
                            val spanPropsMap = spanData.attributes.toMap()
                            shouldExportSpan(spanData.name, spanPropsMap)
                        }.map { spanData ->
                            if (attributesToDrop.isEmpty()) {
                                spanData
                            } else {
                                filterAttributes(spanData.name, spanData.attributes, attributesToDrop) { newAttributes ->
                                    ModifiedSpanData(spanData, newAttributes)
                                } ?: spanData
                            }
                        }.toList()

                delegateExporter.export(filteredSpans)
            }

        private fun shouldExportSpan(
            name: String?,
            propsMap: Map<String, Any?>,
        ): Boolean =
            name == null ||
                sdkConfig.signals.filters.values.anyOrNone(
                    sdkConfig.signals.filters.mode == PulseSignalFilterMode.WHITELIST,
                ) { matchCondition ->
                    signalMatcher.matches(
                        PulseSignalScope.TRACES,
                        name,
                        propsMap,
                        matchCondition,
                    )
                }

        override fun close() {
            delegateExporter.close()
        }
    }

    public inner class SampledLogExporter(
        private val delegateExporter: LogRecordExporter,
    ) : LogRecordExporter by delegateExporter {
        private val attributesToDrop by lazy {
            getDroppedAttributesConfig(PulseSignalScope.LOGS)
        }

        override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
            sampleSession {
                val filteredLogs =
                    logs
                        .asSequence()
                        .filter { logRecord ->
                            val logPropsMap = logRecord.attributes.toMap()
                            val logName = logRecord.bodyValue?.asString()
                            shouldExportLog(logName, logPropsMap)
                        }.map { logRecord ->
                            if (attributesToDrop.isEmpty()) {
                                logRecord
                            } else {
                                filterAttributes(
                                    logRecord.bodyValue?.asString().orEmpty(),
                                    logRecord.attributes,
                                    attributesToDrop,
                                ) { newAttributes ->
                                    ModifiedLAttributeRecordData(newAttributes, logRecord)
                                } ?: logRecord
                            }
                        }.toList()

                delegateExporter.export(filteredLogs)
            }

        private fun shouldExportLog(
            name: String?,
            propsMap: Map<String, Any?>,
        ): Boolean =
            name == null ||
                sdkConfig.signals.filters.values.anyOrNone(
                    sdkConfig.signals.filters.mode == PulseSignalFilterMode.WHITELIST,
                ) { matchCondition ->
                    signalMatcher.matches(
                        PulseSignalScope.LOGS,
                        name,
                        propsMap,
                        matchCondition,
                    )
                }

        override fun close() {
            delegateExporter.close()
        }

        // issue raised at https://github.com/detekt/detekt/issues/8928
        @Suppress("UnnecessaryInnerClass")
        private inner class ModifiedLAttributeRecordData(
            private val attributes: Attributes,
            private val oldLogRecordData: LogRecordData,
        ) : LogRecordData by oldLogRecordData {
            override fun getAttributes(): Attributes = attributes

            override fun getBodyValue(): Value<*>? = oldLogRecordData.getBodyValue()

            override fun getEventName(): String? = oldLogRecordData.eventName
        }
    }

    public inner class SampledMetricExporter(
        private val delegateExporter: MetricExporter,
    ) : MetricExporter by delegateExporter {
        override fun export(metrics: Collection<MetricData>): CompletableResultCode =
            sampleSession {
                val filteredLogs =
                    metrics
                        .asSequence()
                        .filter { metric ->
                            shouldExportMetric(metric.name)
                        }.toList()

                delegateExporter.export(filteredLogs)
            }

        private fun shouldExportMetric(name: String): Boolean =
            sdkConfig.signals.filters.values.anyOrNone(
                sdkConfig.signals.filters.mode == PulseSignalFilterMode.WHITELIST,
            ) { matchCondition ->
                signalMatcher.matches(
                    PulseSignalScope.METRICS,
                    name,
                    emptyMap(),
                    matchCondition,
                )
            }

        override fun getDefaultAggregation(instrumentType: InstrumentType): Aggregation? =
            delegateExporter.getDefaultAggregation(instrumentType)

        override fun getMemoryMode(): MemoryMode = delegateExporter.memoryMode

        override fun close() {
            delegateExporter.close()
        }

        override fun with(
            instrumentType: InstrumentType,
            aggregation: Aggregation,
        ): DefaultAggregationSelector? = delegateExporter.with(instrumentType, aggregation)
    }

    public fun getDisabledFeatures(): List<PulseFeatureName> =
        sdkConfig
            .features
            .filter { PulseSdkName.CURRENT_SDK_NAME in it.sdks && it.sessionSampleRate == 0F }
            .map { it.featureName }

    private inline fun <E> List<E>.anyOrNone(
        shouldMatchAny: Boolean,
        predicate: (E) -> Boolean,
    ): Boolean =
        if (shouldMatchAny) {
            this.any(predicate)
        } else {
            this.none(predicate)
        }

    @OptIn(ExperimentalTypeInference::class)
    @BuilderInference
    private inline fun <S> filterAttributes(
        signalName: String,
        signalAttributes: Attributes,
        attributesToDrop: List<PulseSignalMatchCondition>,
        updateAttributes: (newAttributes: Attributes) -> S,
    ): S? {
        val finalAttributesToDrop =
            attributesToDrop
                .filter {
                    signalName.matchesFromRegexCache(it.name)
                }.flatMap { it.props }
                .groupBy({ it.name }) { it.value }

        if (finalAttributesToDrop.isEmpty()) {
            return null
        }

        val spanAttributes = signalAttributes.toMap()
        if (finalAttributesToDrop.none { it.key in spanAttributes.keys }) {
            return null
        }

        val newAttributes =
            signalAttributes
                .toBuilder()
                .apply {
                    signalAttributes
                        .forEach { key, value ->
                            val keyString = key.toString()
                            if (
                                keyString in finalAttributesToDrop.keys &&
                                finalAttributesToDrop[keyString]?.any {
                                    (it == null && value == null) ||
                                        (it != null && it == value.toString())
                                } == true
                            ) {
                                remove(key)
                            }
                        }
                }.build()
        return updateAttributes(newAttributes)
    }

    private inline fun sampleSession(block: () -> CompletableResultCode): CompletableResultCode =
        if (shouldSampleThisSession) {
            block()
        } else {
            CompletableResultCode.ofSuccess()
        }
}

public fun PulseSamplingSignalProcessors(
    context: Context,
    sdkConfig: PulseSdkConfig,
): PulseSamplingSignalProcessors =
    PulseSamplingSignalProcessors(
        context,
        sdkConfig,
        PulseSignalsAttrMatcher(),
        PulseSessionConfigParser(),
        SecureRandom(),
    )
