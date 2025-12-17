package com.pulse.sampling.core

import com.pulse.otel.utils.toMap
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import io.opentelemetry.android.export.ModifiedSpanData
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.Value
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlin.experimental.ExperimentalTypeInference

public class PulseSamplingSignalProcessors(
    private val sdkConfig: PulseSdkConfig,
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
) {
    private fun getDroppedAttributesConfig(scope: PulseSignalScope): List<PulseSignalMatchCondition> =
        sdkConfig
            .signals
            .attributesToDrop
            .filter { it.scopes.contains(scope) && PulseSdkName.CURRENT_SDK_NAME in it.sdks }

    public inner class SampledSpanExporter(
        private val delegateExporter: SpanExporter,
    ) : SpanExporter by delegateExporter {
        private val attributesToDrop by lazy {
            getDroppedAttributesConfig(PulseSignalScope.TRACES)
        }

        override fun export(spans: Collection<SpanData>): CompletableResultCode? {
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

            return delegateExporter.export(filteredSpans)
        }

        private fun shouldExportSpan(
            spanName: String?,
            spanPropsMap: Map<String, Any?>,
        ): Boolean =
            spanName == null ||
                sdkConfig.signals.filters.values.anyOrNone(
                    sdkConfig.signals.filters.mode == PulseSignalFilterMode.WHITELIST,
                ) { matchCondition ->
                    signalMatcher.matches(
                        PulseSignalScope.TRACES,
                        spanName,
                        spanPropsMap,
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

        override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
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

            return delegateExporter.export(filteredLogs)
        }

        private fun shouldExportLog(
            logName: String?,
            logPropsMap: Map<String, Any?>,
        ): Boolean =
            logName == null ||
                sdkConfig.signals.filters.values.anyOrNone(
                    sdkConfig.signals.filters.mode == PulseSignalFilterMode.WHITELIST,
                ) { matchCondition ->
                    signalMatcher.matches(
                        PulseSignalScope.LOGS,
                        logName,
                        logPropsMap,
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

    public fun getEnabledFeatures(): List<String> =
        sdkConfig
            .features
            .filter { PulseSdkName.CURRENT_SDK_NAME in it.sdks && it.sessionSampleRate == 1F }
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
}
