package com.pulse.sampling.core.exporters

import android.content.Context
import com.pulse.android.api.otel.models.copy
import com.pulse.sampling.core.PulseSessionConfigParser
import com.pulse.sampling.core.PulseSessionParser
import com.pulse.sampling.core.PulseSignalMatcher
import com.pulse.sampling.core.PulseSignalsAttrMatcher
import com.pulse.sampling.models.PulseAttributeType
import com.pulse.sampling.models.PulseAttributesToAddEntry
import com.pulse.sampling.models.PulseAttributesToDropEntry
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseMetricsToAddEntry
import com.pulse.sampling.models.PulseMetricsToAddTarget
import com.pulse.sampling.models.PulseMetricsType
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.PulseSignalsToSampleEntry
import com.pulse.sampling.models.SamplingRate
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import com.pulse.utils.PulseOtelUtils
import com.pulse.utils.filterNot
import com.pulse.utils.matchesFromRegexCache
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.MeterProvider
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
    private val currentSdkName: PulseSdkName,
    private val meterProviderLazy: Lazy<MeterProvider>,
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
    private val sessionParser: PulseSessionParser = PulseSessionConfigParser(),
    private val randomIdGenerator: Random = SecureRandom(),
) {
    private fun getDroppedAttributesConfig(scope: PulseSignalScope): List<PulseAttributesToDropEntry> =
        sdkConfig
            .signals
            .attributesToDrop
            .filter { it.condition.scopes.contains(scope) && currentSdkName in it.condition.sdks }

    private fun getAddedAttributesConfig(scope: PulseSignalScope): List<PulseAttributesToAddEntry> =
        sdkConfig
            .signals
            .attributesToAdd
            .filter { it.condition.scopes.contains(scope) && currentSdkName in it.condition.sdks }

    private fun getMetricsToAddConfig(scope: PulseSignalScope): Map<PulseMetricsToAddEntry, DataRecorderFactory> =
        sdkConfig
            .signals
            .metricsToAdd
            .filter { it.condition.scopes.contains(scope) && currentSdkName in it.condition.sdks }
            .associateWith { creatMeter(it) }

    private fun getSignalsToSampleConfig(scope: PulseSignalScope): List<PulseSignalsToSampleEntry> =
        sdkConfig
            .sampling
            .signalsToSample
            .filter { it.condition.scopes.contains(scope) && currentSdkName in it.condition.sdks }

    private val sessionRandomValue by lazy { randomIdGenerator.nextFloat() }

    private val shouldSampleThisSession by lazy {
        val samplingRate = sessionParser.parses(context, sdkConfig.sampling, currentSdkName)
        sessionRandomValue < samplingRate
    }

    private fun shouldSampleByRate(rate: SamplingRate): Boolean = sessionRandomValue < rate

    public inner class SampledSpanExporter(
        private val delegateExporter: SpanExporter,
    ) : SpanExporter by delegateExporter {
        private val attributesToDrop by lazy {
            getDroppedAttributesConfig(PulseSignalScope.TRACES)
        }

        private val attributesToAdd by lazy {
            getAddedAttributesConfig(PulseSignalScope.TRACES)
        }

        private val metricsToAdd by lazy {
            getMetricsToAddConfig(PulseSignalScope.TRACES)
        }

        override fun export(spans: Collection<SpanData>): CompletableResultCode =
            sampleSpansInSession(
                signals = spans,
                attributesToAdd = attributesToAdd,
                attributesToDrop = attributesToDrop,
                metricsToAdd = metricsToAdd,
            ) {
                val filteredSpans =
                    it
                        .asSequence()
                        .filter { spanData ->
                            shouldExportSpan(spanData.name, spanData.attributes)
                        }.toList()

                delegateExporter.export(filteredSpans)
            }

        private fun shouldExportSpan(
            name: String?,
            propsMap: Attributes,
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
                        currentSdkName,
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

        private val metricsToAdd by lazy {
            getMetricsToAddConfig(PulseSignalScope.LOGS)
        }

        override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
            sampleLogsInSession(
                logs,
                attributesToAdd,
                attributesToDrop,
                metricsToAdd,
            ) {
                val filteredLogs =
                    it
                        .asSequence()
                        .filter { logRecord ->
                            val logName = logRecord.bodyValue?.asString()
                            shouldExportLog(logName, logRecord.attributes)
                        }.toList()

                delegateExporter.export(filteredLogs)
            }

        private fun shouldExportLog(
            name: String?,
            propsMap: Attributes,
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
                        currentSdkName,
                    )
                }

        override fun close() {
            delegateExporter.close()
        }
    }

    public inner class SampledMetricExporter(
        private val delegateExporter: MetricExporter,
    ) : MetricExporter by delegateExporter {
        override fun export(metrics: Collection<MetricData>): CompletableResultCode =
            sampleMetricsInSession(
                signals = metrics,
                attributesToDrop = emptyList(),
                attributesToAdd = emptyList(),
            ) {
                val filteredLogs =
                    it
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
                    Attributes.empty(),
                    matchCondition,
                    currentSdkName,
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

    public fun getEnabledFeatures(): List<PulseFeatureName> =
        sdkConfig
            .features
            .filter { currentSdkName in it.sdks && it.sessionSampleRate == 1F }
            .map { it.featureName }

    private inline fun <E> Iterable<E>.anyOrNone(
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
    private inline fun <S> dropAttributes(
        signal: S,
        scope: PulseSignalScope,
        attributesToDrop: Collection<PulseAttributesToDropEntry>,
        signalValuesProvider: S.() -> SignalMatchValues,
        attributesModifier: S.(newAttributes: Attributes) -> S,
    ): S? {
        val (signalName, signalAttributes) = signal.signalValuesProvider()
        val finalAttributesToDrop =
            attributesToDrop
                .filter {
                    signalMatcher.matches(
                        scope = scope,
                        name = signalName,
                        props = signalAttributes,
                        signalMatchConfig = it.condition,
                        sdkName = currentSdkName,
                    )
                }

        if (finalAttributesToDrop.isEmpty()) {
            return null
        }

        val keysToDrop = finalAttributesToDrop.flatMap { it.values }

        val newSignalAttributes =
            signalAttributes.filterNot { key ->
                keysToDrop.any { key.key.matchesFromRegexCache(it) }
            }
        return signal.attributesModifier(newSignalAttributes)
    }

    @OptIn(ExperimentalTypeInference::class)
    @BuilderInference
    private inline fun <S> addAttributes(
        signal: S,
        scope: PulseSignalScope,
        attributesToAdd: Collection<PulseAttributesToAddEntry>,
        signalValuesProvider: S.() -> SignalMatchValues,
        attributesModifier: S.(newAttributes: Attributes) -> S,
    ): S? {
        val (signalName, signalAttributes) = signal.signalValuesProvider()
        val matchingEntries =
            attributesToAdd
                .filter {
                    signalName.matchesFromRegexCache(it.condition.name)
                }

        if (matchingEntries.isEmpty()) {
            return null
        }

        val entriesThatMatch =
            matchingEntries.filter { entry ->
                signalMatcher.matches(
                    scope,
                    signalName,
                    signalAttributes,
                    entry.condition,
                    currentSdkName,
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
        return signal.attributesModifier(newAttributes)
    }

    private fun parseStringArray(value: String): List<String> = value.split(",")

    private fun parseBooleanArray(value: String): List<Boolean> = value.split(",").mapNotNull { it.trim().toBooleanStrictOrNull() }

    private fun parseLongArray(value: String): List<Long> = value.split(",").mapNotNull { it.trim().toLongOrNull() }

    private fun parseDoubleArray(value: String): List<Double> = value.split(",").mapNotNull { it.trim().toDoubleOrNull() }

    private inline fun sampleLogsInSession(
        signals: Collection<LogRecordData>,
        attributesToAdd: List<PulseAttributesToAddEntry>,
        attributesToDrop: List<PulseAttributesToDropEntry>,
        metricsToAdd: Map<PulseMetricsToAddEntry, DataRecorderFactory>,
        block: (Collection<LogRecordData>) -> CompletableResultCode,
    ): CompletableResultCode =
        sampleSession(
            scope = PulseSignalScope.LOGS,
            attributesToAdd = attributesToAdd,
            attributesToDrop = attributesToDrop,
            metricsToAdd = metricsToAdd,
            signals = signals,
            signalValuesProvider = LogRecordData::toSignalValues,
            attributesModifier = { this.copy(attributes = it) },
            block = block,
        )

    private inline fun sampleSpansInSession(
        signals: Collection<SpanData>,
        attributesToAdd: Collection<PulseAttributesToAddEntry>,
        attributesToDrop: Collection<PulseAttributesToDropEntry>,
        metricsToAdd: Map<PulseMetricsToAddEntry, DataRecorderFactory>,
        block: (Collection<SpanData>) -> CompletableResultCode,
    ): CompletableResultCode =
        sampleSession(
            scope = PulseSignalScope.TRACES,
            attributesToAdd = attributesToAdd,
            attributesToDrop = attributesToDrop,
            metricsToAdd = metricsToAdd,
            signals = signals,
            attributesModifier = { this.copy(attributes = it) },
            signalValuesProvider = SpanData::toSignalValues,
            block = block,
        )

    private inline fun sampleMetricsInSession(
        signals: Collection<MetricData>,
        attributesToAdd: List<PulseAttributesToAddEntry>,
        attributesToDrop: List<PulseAttributesToDropEntry>,
        block: (Collection<MetricData>) -> CompletableResultCode,
    ): CompletableResultCode =
        sampleSession(
            scope = PulseSignalScope.METRICS,
            attributesToAdd = attributesToAdd,
            attributesToDrop = attributesToDrop,
            metricsToAdd = emptyMap(),
            attributesModifier = { this },
            signalValuesProvider = MetricData::toSignalValues,
            signals = signals,
            block = block,
        )

    private inline fun <M> Iterable<M>.observerAndModifyData(
        scope: PulseSignalScope,
        attributesToAdd: Collection<PulseAttributesToAddEntry>,
        attributesToDrop: Collection<PulseAttributesToDropEntry>,
        metricsToAdd: Map<PulseMetricsToAddEntry, DataRecorderFactory>,
        signalValuesProvider: M.() -> SignalMatchValues,
        attributesModifier: M.(newAttributes: Attributes) -> M,
    ): List<M> =
        this.map { signal ->
            val addedAttributesSignal =
                if (attributesToAdd.isNotEmpty()) {
                    addAttributes(
                        signal,
                        scope,
                        attributesToAdd,
                        signalValuesProvider,
                        attributesModifier,
                    ) ?: signal
                } else {
                    signal
                }

            if (metricsToAdd.isNotEmpty()) {
                addMetrics(addedAttributesSignal, scope, metricsToAdd, signalValuesProvider)
            }

            if (attributesToDrop.isNotEmpty()) {
                dropAttributes(addedAttributesSignal, scope, attributesToDrop, signalValuesProvider, attributesModifier)
                    ?: addedAttributesSignal
            } else {
                addedAttributesSignal
            }
        }

    private fun buildAttributesFromPick(
        signalAttributes: Attributes,
        attributesToPick: Collection<PulseSignalMatchCondition>?,
    ): Attributes {
        if (attributesToPick.isNullOrEmpty()) return Attributes.empty()
        val keyPatterns = attributesToPick.flatMap { it.props }.map { it.name }
        if (keyPatterns.isEmpty()) return Attributes.empty()
        val builder = Attributes.builder()
        signalAttributes.forEach { key, value ->
            if (keyPatterns.any { key.key.matchesFromRegexCache(it) }) {
                @Suppress("UNCHECKED_CAST")
                builder.put(key as AttributeKey<Any>, value)
            }
        }
        return builder.build()
    }

    private inline fun <M> addMetrics(
        signal: M,
        scope: PulseSignalScope,
        metricsToAdd: Map<PulseMetricsToAddEntry, DataRecorderFactory>,
        signalValuesProvider: M.() -> SignalMatchValues,
    ) {
        val (name, props) = signal.signalValuesProvider()
        metricsToAdd.forEach { (metricsToAddEntry, recorderFactory) ->
            if (
                signalMatcher.matches(
                    scope,
                    name,
                    props,
                    metricsToAddEntry.condition,
                    currentSdkName,
                )
            ) {
                val pickedAttributes = buildAttributesFromPick(props, metricsToAddEntry.attributesToPick)
                val baseMetricName = PulseOtelUtils.sanitizeInstrumentationName(metricsToAddEntry.name)
                when (val target = metricsToAddEntry.target) {
                    is PulseMetricsToAddTarget.Attribute -> {
                        props.forEach { key, value ->
                            if (target.condition.props.any { key.key.matchesFromRegexCache(it.name) }) {
                                val metricName =
                                    if (target.shouldAddPropNameAsSuffix) {
                                        "$baseMetricName.${key.key}"
                                    } else {
                                        baseMetricName
                                    }
                                recorderFactory(metricName)(value, pickedAttributes)
                            }
                        }
                    }

                    is PulseMetricsToAddTarget.Name -> {
                        recorderFactory(baseMetricName)(name, pickedAttributes)
                    }
                }
            }
        }
    }

    private fun creatMeter(meterConfigEntry: PulseMetricsToAddEntry): DataRecorderFactory {
        val meter = meterProviderLazy.value.meterBuilder("$INSTRUMENTATION_NAME_PREFIX.metric").build()
        val recorderCache = mutableMapOf<String, DataRecorder>()

        return { metricName: String ->
            recorderCache.getOrPut(metricName) {
                when (val data = meterConfigEntry.type) {
                    is PulseMetricsType.Counter -> {
                        val counter = meter.counterBuilder(metricName).build()
                        val recorder: DataRecorder = { _, attributes -> counter.add(1, attributes) }
                        recorder
                    }

                    is PulseMetricsType.Gauge -> {
                        if (data.isFraction) {
                            val gauge = meter.gaugeBuilder(metricName).build()
                            val recorder: DataRecorder = { value, attributes ->
                                value.toString().toDoubleOrNull()?.let { gauge.set(it, attributes) }
                            }
                            recorder
                        } else {
                            val gauge = meter.gaugeBuilder(metricName).ofLongs().build()
                            val recorder: DataRecorder = { value, attributes ->
                                value.toString().toLongOrNull()?.let { gauge.set(it, attributes) }
                            }
                            recorder
                        }
                    }

                    is PulseMetricsType.Histogram -> {
                        val histogramBuilder = meter.histogramBuilder(metricName)
                        val bucketBoundaries = data.bucket?.map { it.toDouble() }.orEmpty()
                        if (bucketBoundaries.isNotEmpty()) {
                            histogramBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries)
                        }
                        if (data.isFraction) {
                            val histogram = histogramBuilder.build()
                            val recorder: DataRecorder = { value, attributes ->
                                value.toString().toDoubleOrNull()?.let { histogram.record(it, attributes) }
                            }
                            recorder
                        } else {
                            val histogram = histogramBuilder.ofLongs().build()
                            val recorder: DataRecorder = { value, attributes ->
                                value.toString().toLongOrNull()?.let { histogram.record(it, attributes) }
                            }
                            recorder
                        }
                    }

                    is PulseMetricsType.Sum -> {
                        when {
                            data.isFraction && data.isMonotonic -> {
                                val counter = meter.counterBuilder(metricName).ofDoubles().build()
                                val recorder: DataRecorder = { value, attributes ->
                                    value.toString().toDoubleOrNull()?.let { counter.add(it, attributes) }
                                }
                                recorder
                            }

                            data.isFraction && !data.isMonotonic -> {
                                val upDownCounter = meter.upDownCounterBuilder(metricName).ofDoubles().build()
                                val recorder: DataRecorder = { value, attributes ->
                                    value.toString().toDoubleOrNull()?.let { upDownCounter.add(it, attributes) }
                                }
                                recorder
                            }

                            !data.isFraction && data.isMonotonic -> {
                                val counter = meter.counterBuilder(metricName).build()
                                val recorder: DataRecorder = { value, attributes ->
                                    value.toString().toLongOrNull()?.let { counter.add(it, attributes) }
                                }
                                recorder
                            }

                            else -> {
                                val upDownCounter = meter.upDownCounterBuilder(metricName).build()
                                val recorder: DataRecorder = { value, attributes ->
                                    value.toString().toLongOrNull()?.let { upDownCounter.add(it, attributes) }
                                }
                                recorder
                            }
                        }
                    }
                }
            }
        }
    }

    private inline fun <M> sampleSession(
        scope: PulseSignalScope,
        attributesToAdd: Collection<PulseAttributesToAddEntry>,
        attributesToDrop: Collection<PulseAttributesToDropEntry>,
        metricsToAdd: Map<PulseMetricsToAddEntry, DataRecorderFactory>,
        signals: Collection<M>,
        signalValuesProvider: M.() -> SignalMatchValues,
        attributesModifier: M.(newAttributes: Attributes) -> M,
        block: (Collection<M>) -> CompletableResultCode,
    ): CompletableResultCode {
        val modifiedSignals =
            signals.observerAndModifyData(scope, attributesToAdd, attributesToDrop, metricsToAdd, signalValuesProvider, attributesModifier)
        val signalsToSample = getSignalsToSampleConfig(scope)
        val sampledSignals =
            modifiedSignals.filter { signal ->
                val (name, props) = signal.signalValuesProvider()
                val matchedEntry =
                    signalsToSample.firstOrNull { entry ->
                        signalMatcher.matches(
                            scope,
                            name,
                            props,
                            entry.condition,
                            currentSdkName,
                        )
                    }
                if (matchedEntry != null) {
                    shouldSampleByRate(matchedEntry.sampleRate)
                } else {
                    if (shouldSampleThisSession) {
                        true
                    } else {
                        sdkConfig.sampling.criticalEventPolicies
                            ?.run {
                                alwaysSend.any { matchCondition ->
                                    signalMatcher.matches(
                                        scope,
                                        name,
                                        props,
                                        matchCondition,
                                        currentSdkName,
                                    )
                                }
                            } == true
                    }
                }
            }
        return if (sampledSignals.isNotEmpty()) {
            block(sampledSignals)
        } else {
            CompletableResultCode.ofSuccess()
        }
    }

    private companion object {
        private const val INSTRUMENTATION_NAME_PREFIX = "com.pulse.signal.processors"
    }
}

public fun PulseSamplingSignalProcessors(
    context: Context,
    sdkConfig: PulseSdkConfig,
    currentSdkName: PulseSdkName,
    meterProviderLazy: Lazy<MeterProvider>,
): PulseSamplingSignalProcessors =
    PulseSamplingSignalProcessors(
        context = context,
        sdkConfig = sdkConfig,
        currentSdkName = currentSdkName,
        meterProviderLazy = meterProviderLazy,
        signalMatcher = PulseSignalsAttrMatcher(),
        sessionParser = PulseSessionConfigParser(),
        randomIdGenerator = SecureRandom(),
    )

internal data class SignalMatchValues(
    val name: String,
    val props: Attributes,
)

internal fun LogRecordData.toSignalValues() = SignalMatchValues(bodyValue?.asString().orEmpty(), attributes)

internal fun SpanData.toSignalValues() = SignalMatchValues(name.orEmpty(), attributes)

internal fun MetricData.toSignalValues() = SignalMatchValues(this.name, Attributes.empty())

private typealias DataRecorder = (value: Any, attributes: Attributes) -> Unit
private typealias DataRecorderFactory = (metricName: String) -> DataRecorder
