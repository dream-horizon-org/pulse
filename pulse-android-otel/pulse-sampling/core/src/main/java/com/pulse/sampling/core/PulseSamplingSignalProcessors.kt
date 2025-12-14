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
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlin.experimental.ExperimentalTypeInference

public class PulseSamplingSignalProcessors(
    private val sdkConfig: PulseSdkConfig,
    private val sampler: PulseMatcher = PulseSignalsAttrMatcher(),
) {
    private fun getDroppedAttributesConfig(scope: PulseSignalScope): List<PulseSignalMatchCondition> = sdkConfig
        .signals
        .attributesToDrop
        .filter { it.scopes.contains(scope) && PulseSdkName.CURRENT_SDK_NAME in it.sdks }


    public inner class SampledSpanProcessor(
        private val delegateProcessor: SpanProcessor,
    ) : SpanProcessor by delegateProcessor {
        override fun onEnd(span: ReadableSpan) {
            // todo how to handle case when there is child trace but parent we are sampling?
            val spanPropsMap = span.attributes.toMap()
            sampleWithFilterMode(PulseSignalScope.TRACES, span.name, spanPropsMap) {
                delegateProcessor.onEnd(span)
            }
        }

        override fun isEndRequired(): Boolean = true

        override fun shutdown(): CompletableResultCode {
            return delegateProcessor.shutdown()
        }

        override fun forceFlush(): CompletableResultCode {
            return delegateProcessor.forceFlush()
        }

        override fun close() {
            delegateProcessor.close()
        }
    }

    public inner class FilteredSpanExporter(
        private val delegateExporter: SpanExporter,
    ) : SpanExporter by delegateExporter {
        private val attributesToDrop by lazy {
            getDroppedAttributesConfig(PulseSignalScope.TRACES)
        }

        override fun export(spans: Collection<SpanData>): CompletableResultCode? {
            if (attributesToDrop.isEmpty()) {
                return delegateExporter.export(spans)
            }

            val newSpans = spans
                .asSequence()
                .map { spanData ->
                    filterAttributes(spanData.name, spanData.attributes, attributesToDrop) { newAttributes ->
                        ModifiedSpanData(spanData, newAttributes)
                    } ?: spanData
                }
                .toList()
            return delegateExporter.export(newSpans)
        }

        override fun close() {
            delegateExporter.close()
        }
    }

    public inner class SampledLogsProcessor(
        private val delegateLogProcessor: LogRecordProcessor,
    ) : LogRecordProcessor {
        override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
            val spanPropsMap = logRecord.attributes.toMap()
            val logName = logRecord.bodyValue?.asString()
            sampleWithFilterMode(PulseSignalScope.LOGS, logName, spanPropsMap) {
                delegateLogProcessor.onEmit(context, logRecord)
            }
        }
    }

    public inner class FilteredLogExporter(
        private val delegateExporter: LogRecordExporter,
    ) : LogRecordExporter by delegateExporter {
        private val attributesToDrop by lazy {
            getDroppedAttributesConfig(PulseSignalScope.LOGS)
        }

        override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
            if (attributesToDrop.isEmpty()) {
                return delegateExporter.export(logs)
            }

            val newLogs = logs
                .asSequence()
                .map { logRecord: LogRecordData ->
                    filterAttributes(
                        // todo handle null
                        logRecord.bodyValue?.asString().orEmpty(),
                        logRecord.attributes,
                        attributesToDrop,
                    ) { newAttributes ->
                        ModifiedLAttributeRecordData(newAttributes, logRecord)
                    } ?: logRecord
                }
                .toList()
            return delegateExporter.export(newLogs)
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
            override fun getAttributes(): Attributes {
                return attributes
            }

            override fun getBodyValue(): Value<*>? {
                return oldLogRecordData.getBodyValue()
            }

            override fun getEventName(): String? {
                return oldLogRecordData.eventName
            }

        }
    }

    private inline fun <E> List<E>.anyOrNone(shouldMatchAny: Boolean, predicate: (E) -> Boolean): Boolean {
        return if (shouldMatchAny) {
            this.any(predicate)
        } else {
            this.none(predicate)
        }
    }

    private inline fun sampleWithFilterMode(
        scope: PulseSignalScope,
        signalName: String?,
        signalMap: Map<String, Any?>,
        onSampled: () -> Unit,
    ) {
        if (
            signalName == null ||
            sdkConfig.signals.filters.values.anyOrNone(
                sdkConfig.signals.filters.mode == PulseSignalFilterMode.WHITELIST,
            ) { matchCondition ->
                sampler.matches(
                    scope,
                    signalName,
                    signalMap,
                    matchCondition,
                )
            }
        ) {
            onSampled()
        }
    }

    @OptIn(ExperimentalTypeInference::class)
    @BuilderInference
    private inline fun <S> filterAttributes(
        signalName: String,
        signalAttributes: Attributes,
        attributesToDrop: List<PulseSignalMatchCondition>,
        updateAttributes: (newAttributes: Attributes) -> S,
    ): S? {
        val finalAttributesToDrop = attributesToDrop
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

        val newAttributes = signalAttributes.toBuilder().apply {
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
