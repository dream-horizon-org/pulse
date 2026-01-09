package com.pulse.sampling.core

import android.content.Context
import com.pulse.otel.utils.matchesFromRegexCache
import com.pulse.otel.utils.toMap
import com.pulse.sampling.models.PulseAttributeType
import com.pulse.sampling.models.PulseAttributesToAddEntry
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import io.opentelemetry.android.export.ModifiedSpanData
import io.opentelemetry.api.common.AttributeKey
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

    private fun getAddedAttributesConfig(scope: PulseSignalScope): List<PulseAttributesToAddEntry> =
        sdkConfig
            .signals
            .attributesToAdd
            .filter { it.condition.scopes.contains(scope) && PulseSdkName.CURRENT_SDK_NAME in it.condition.sdks }

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

        private val attributesToAdd by lazy {
            getAddedAttributesConfig(PulseSignalScope.TRACES)
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
                            var currentAttributes = spanData.attributes
                            var modifiedSpanData: SpanData = spanData

                            if (attributesToDrop.isNotEmpty()) {
                                val droppedResult =
                                    filterAttributes(spanData.name, currentAttributes, attributesToDrop) { newAttributes ->
                                        ModifiedSpanData(spanData, newAttributes)
                                    }
                                if (droppedResult != null) {
                                    modifiedSpanData = droppedResult
                                    currentAttributes = droppedResult.attributes
                                }
                            }

                            if (attributesToAdd.isNotEmpty()) {
                                val addedResult =
                                    addAttributes(
                                        spanData.name,
                                        currentAttributes,
                                        PulseSignalScope.TRACES,
                                        attributesToAdd,
                                    ) { newAttributes ->
                                        ModifiedSpanData(spanData, newAttributes)
                                    }
                                if (addedResult != null) {
                                    modifiedSpanData = addedResult
                                }
                            }

                            modifiedSpanData
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

        private val attributesToAdd by lazy {
            getAddedAttributesConfig(PulseSignalScope.LOGS)
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
                            var currentAttributes = logRecord.attributes
                            var modifiedLogRecord: LogRecordData = logRecord
                            val logName = logRecord.bodyValue?.asString().orEmpty()

                            // First drop attributes if needed
                            if (attributesToDrop.isNotEmpty()) {
                                val droppedResult =
                                    filterAttributes(
                                        logName,
                                        currentAttributes,
                                        attributesToDrop,
                                    ) { newAttributes ->
                                        ModifiedLAttributeRecordData(newAttributes, logRecord)
                                    }
                                if (droppedResult != null) {
                                    modifiedLogRecord = droppedResult
                                    currentAttributes = droppedResult.attributes
                                }
                            }

                            // Then add attributes if needed
                            if (attributesToAdd.isNotEmpty()) {
                                val addedResult =
                                    addAttributes(
                                        logName,
                                        currentAttributes,
                                        PulseSignalScope.LOGS,
                                        attributesToAdd,
                                    ) { newAttributes ->
                                        ModifiedLAttributeRecordData(newAttributes, logRecord)
                                    }
                                if (addedResult != null) {
                                    modifiedLogRecord = addedResult
                                }
                            }

                            modifiedLogRecord
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

    @OptIn(ExperimentalTypeInference::class)
    @BuilderInference
    private inline fun <S> addAttributes(
        signalName: String,
        signalAttributes: Attributes,
        scope: PulseSignalScope,
        attributesToAdd: List<PulseAttributesToAddEntry>,
        updateAttributes: (newAttributes: Attributes) -> S,
    ): S? {
        val matchingEntries =
            attributesToAdd
                .filter {
                    signalName.matchesFromRegexCache(it.condition.name)
                }

        if (matchingEntries.isEmpty()) {
            return null
        }

        val spanAttributes = signalAttributes.toMap()
        val entriesThatMatch =
            matchingEntries.filter { entry ->
                signalMatcher.matches(
                    scope,
                    signalName,
                    spanAttributes,
                    entry.condition,
                )
            }

        if (entriesThatMatch.isEmpty()) {
            return null
        }

        val attributesToAddList = entriesThatMatch.flatMap { it.values }

        if (attributesToAddList.isEmpty()) {
            return null
        }

        val newAttributes =
            signalAttributes
                .toBuilder()
                .apply {
                    attributesToAddList.forEach { attrValue ->
                        when (attrValue.type) {
                            PulseAttributeType.STRING -> {
                                put(attrValue.name, attrValue.value)
                            }

                            PulseAttributeType.BOOLEAN -> {
                                put(attrValue.name, attrValue.value.toBooleanStrictOrNull() ?: return@forEach)
                            }

                            PulseAttributeType.LONG -> {
                                put(attrValue.name, attrValue.value.toLongOrNull() ?: return@forEach)
                            }

                            PulseAttributeType.DOUBLE -> {
                                put(attrValue.name, attrValue.value.toDoubleOrNull() ?: return@forEach)
                            }

                            PulseAttributeType.STRING_ARRAY -> {
                                val arrayValue = parseStringArray(attrValue.value)
                                put(AttributeKey.stringArrayKey(attrValue.name), arrayValue)
                            }

                            PulseAttributeType.BOOLEAN_ARRAY -> {
                                val arrayValue = parseBooleanArray(attrValue.value)
                                put(AttributeKey.booleanArrayKey(attrValue.name), arrayValue)
                            }

                            PulseAttributeType.LONG_ARRAY -> {
                                val arrayValue = parseLongArray(attrValue.value)
                                put(AttributeKey.longArrayKey(attrValue.name), arrayValue)
                            }

                            PulseAttributeType.DOUBLE_ARRAY -> {
                                val arrayValue = parseDoubleArray(attrValue.value)
                                put(AttributeKey.doubleArrayKey(attrValue.name), arrayValue)
                            }
                        }
                    }
                }.build()
        return updateAttributes(newAttributes)
    }

    private fun parseStringArray(value: String): List<String> = value.split(",")

    private fun parseBooleanArray(value: String): List<Boolean> = value.split(",").mapNotNull { it.trim().toBooleanStrictOrNull() }

    private fun parseLongArray(value: String): List<Long> = value.split(",").mapNotNull { it.trim().toLongOrNull() }

    private fun parseDoubleArray(value: String): List<Double> = value.split(",").mapNotNull { it.trim().toDoubleOrNull() }

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
