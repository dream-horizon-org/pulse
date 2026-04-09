/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Sampling exporters: add → metrics → drop → targeted sampling (signalsToSample) → session sampling (Batch 4, LLD §8).
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk
import Security

// MARK: - PulseSamplingSignalProcessors

/// Closure that records a value into a metric instrument.
/// - Parameters:
///   - value: The signal name (for .name target) or attribute value (for .attribute target). For counters with .name target, value is ignored and 1 is added.
///   - attributeKeyForSuffix: When addPropNameAsSuffix is true, the attribute key to suffix to the metric name (e.g. "http.method"). Nil otherwise.
///   - pointAttributes: Attributes to attach to the metric data point (from attributesToPick).
public typealias DataRecorder = (Any?, _ attributeKeyForSuffix: String?, _ pointAttributes: [String: AttributeValue]) -> Void

/// Holds SampledSpanExporter, SampledLogExporter, SampledMetricExporter and getEnabledFeatures.
public final class PulseSamplingSignalProcessors {
    private let sdkConfig: PulseSdkConfig
    private let currentSdkName: PulseSdkName
    private let signalMatcher: PulseSignalMatcher
    private let sessionParser: PulseSessionParser
    private let sessionSamplingDecision: PulseSessionSamplingDecision
    private let randomGenerator: () -> Float
    private let regexCache = SamplingRegexCache()
    private var meterProviderForMetricsToAdd: (any MeterProvider)?

    /// Injects the MeterProvider for metrics-to-add (called by PulseKit after building the metric pipeline).
    /// When nil, createMeter uses a standalone provider; metrics are recorded but not exported.
    internal func setMeterProviderForMetricsToAdd(_ provider: (any MeterProvider)?) {
        meterProviderForMetricsToAdd = provider
    }

    public init(
        deviceContext: PulseDeviceContext = .current,
        sdkConfig: PulseSdkConfig,
        currentSdkName: PulseSdkName,
        signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
        sessionParser: PulseSessionParser = PulseSessionConfigParser(),
        randomGenerator: (() -> Float)? = nil,
        meterProviderForMetricsToAdd: (any MeterProvider)? = nil
    ) {
        self.sdkConfig = sdkConfig
        self.currentSdkName = currentSdkName
        self.signalMatcher = signalMatcher
        self.sessionParser = sessionParser
        self.randomGenerator = randomGenerator ?? { Float.random(in: 0...1) }
        self.meterProviderForMetricsToAdd = meterProviderForMetricsToAdd
        sessionSamplingDecision = PulseSessionSamplingDecision(
            deviceContext: deviceContext,
            samplingConfig: sdkConfig.sampling,
            currentSdkName: currentSdkName,
            parser: sessionParser,
            randomGenerator: self.randomGenerator
        )
    }

    // MARK: - Attribute config (scoped)

    private func getDroppedAttributesConfig(scope: PulseSignalScope) -> [PulseAttributesToDropEntry] {
        sdkConfig.signals.attributesToDrop.filter {
            $0.condition.scopes.contains(scope) && $0.condition.sdks.contains(currentSdkName)
        }
    }

    private func getAddedAttributesConfig(scope: PulseSignalScope) -> [PulseAttributesToAddEntry] {
        sdkConfig.signals.attributesToAdd.filter {
            $0.condition.scopes.contains(scope) && $0.condition.sdks.contains(currentSdkName)
        }
    }

    /// Returns metricsToAdd entries that apply to the given scope and SDK, each paired with its DataRecorder.
    internal func getMetricsToAddConfig(scope: PulseSignalScope) -> [(PulseMetricsToAddEntry, DataRecorder)] {
        sdkConfig.signals.metricsToAdd
            .filter { $0.condition.scopes.contains(scope) && $0.condition.sdks.contains(currentSdkName) }
            .map { ($0, createMeter(entry: $0)) }
    }

    private func createMeter(entry: PulseMetricsToAddEntry) -> DataRecorder {
        let provider = meterProviderForMetricsToAdd ?? MeterProviderSdk.builder().build() as any MeterProvider
        let meter = provider.meterBuilder(name: "com.pulse.signal.processors.metric").build()
        let baseSanitizedName = PulseOtelUtils.sanitizeMetricName(name: entry.name)

        // For .attribute with addPropNameAsSuffix, we need a cache of recorders per metric name.
        let useAddPropNameAsSuffix: Bool
        if case .attribute(_, let suffix) = entry.target {
            useAddPropNameAsSuffix = suffix
        } else {
            useAddPropNameAsSuffix = false
        }

        if useAddPropNameAsSuffix {
            var cache: [String: (Any?, [String: AttributeValue]) -> Void] = [:]
            let lock = NSLock()
            return { value, attrKey, attrs in
                guard let attrKey = attrKey else { return }
                let fullName = baseSanitizedName + "." + PulseOtelUtils.sanitizeMetricName(name: attrKey)
                let recorder: (Any?, [String: AttributeValue]) -> Void = lock.withLock {
                    if let cached = cache[fullName] { return cached }
                    let inner = self.createInstrumentRecorder(entry: entry, meter: meter, sanitizedName: fullName)
                    cache[fullName] = inner
                    return inner
                }
                recorder(value, attrs)
            }
        }

        let recorder = createInstrumentRecorder(entry: entry, meter: meter, sanitizedName: baseSanitizedName)
        return { value, _, attrs in recorder(value, attrs) }
    }

    /// Creates a closure that records (value, attrs) into an instrument with the given name.
    private func createInstrumentRecorder(
        entry: PulseMetricsToAddEntry,
        meter: any Meter,
        sanitizedName: String
    ) -> (Any?, [String: AttributeValue]) -> Void {
        switch entry.type {
        case .counter:
            var counter = meter.counterBuilder(name: sanitizedName).build()
            return { _, attrs in counter.add(value: 1, attributes: attrs) }
        case .gauge(let isFraction):
            if isFraction {
                var gauge = meter.gaugeBuilder(name: sanitizedName).build()
                return { value, attrs in
                    guard let val = value else { return }
                    let s = String(describing: val)
                    guard let d = Double(s) else { return }
                    gauge.record(value: d, attributes: attrs)
                }
            } else {
                var gauge = meter.gaugeBuilder(name: sanitizedName).ofLongs().build()
                return { value, attrs in
                    guard let val = value else { return }
                    let s = String(describing: val)
                    guard let l = Int64(s) else { return }
                    gauge.record(value: Int(l), attributes: attrs)
                }
            }
        case .histogram(let bucket, let isFraction):
            let builder = meter.histogramBuilder(name: sanitizedName)
            if let buckets = bucket, !buckets.isEmpty {
                _ = builder.setExplicitBucketBoundariesAdvice(buckets)
            }
            if isFraction {
                var histogram = builder.build()
                return { value, attrs in
                    guard let val = value else { return }
                    let s = String(describing: val)
                    guard let d = Double(s) else { return }
                    histogram.record(value: d, attributes: attrs)
                }
            } else {
                var histogram = builder.ofLongs().build()
                return { value, attrs in
                    guard let val = value else { return }
                    let s = String(describing: val)
                    guard let l = Int64(s) else { return }
                    histogram.record(value: Int(l), attributes: attrs)
                }
            }
        case .sum(let isFraction, let isMonotonic):
            if isMonotonic {
                if isFraction {
                    var counter = meter.counterBuilder(name: sanitizedName).ofDoubles().build()
                    return { value, attrs in
                        guard let val = value else { return }
                        let s = String(describing: val)
                        guard let d = Double(s) else { return }
                        counter.add(value: d, attributes: attrs)
                    }
                } else {
                    var counter = meter.counterBuilder(name: sanitizedName).build()
                    return { value, attrs in
                        guard let val = value else { return }
                        let s = String(describing: val)
                        guard let l = Int64(s) else { return }
                        counter.add(value: Int(l), attributes: attrs)
                    }
                }
            }
            if isFraction {
                var sum = meter.upDownCounterBuilder(name: sanitizedName).ofDoubles().build()
                return { value, attrs in
                    guard let val = value else { return }
                    let s = String(describing: val)
                    guard let d = Double(s) else { return }
                    sum.add(value: d, attributes: attrs)
                }
            } else {
                var sum = meter.upDownCounterBuilder(name: sanitizedName).build()
                return { value, attrs in
                    guard let val = value else { return }
                    let s = String(describing: val)
                    guard let l = Int64(s) else { return }
                    sum.add(value: Int(l), attributes: attrs)
                }
            }
        }
    }

    private func stringFromAny(_ value: Any?) -> String {
        guard let value = value else { return "" }
        if let s = value as? String { return s }
        if let b = value as? Bool { return String(b) }
        if let i = value as? Int { return String(i) }
        if let d = value as? Double { return String(d) }
        if let arr = value as? [String] { return arr.joined(separator: ",") }
        return String(describing: value)
    }

    /// Builds attributes for a metric data point from signal attributes, matching keys against attributesToPick conditions.
    private func buildAttributesToPick(
        from signalAttributes: [String: AttributeValue],
        entry: PulseMetricsToAddEntry
    ) -> [String: AttributeValue] {
        guard !entry.attributesToPick.isEmpty else { return [:] }
        var result: [String: AttributeValue] = [:]
        for (key, value) in signalAttributes {
            let keyMatches = entry.attributesToPick.contains { condition in
                condition.props.contains { prop in
                    regexCache.matches(string: key, pattern: prop.name)
                }
            }
            if keyMatches {
                result[key] = value
            }
        }
        return result
    }

    /// Records metrics for signals that match metricsToAdd conditions.
    private func recordMetricsForSignal(
        scope: PulseSignalScope,
        name: String?,
        props: [String: Any?],
        signalAttributes: [String: AttributeValue],
        metricsToAdd: [(PulseMetricsToAddEntry, DataRecorder)]
    ) {
        guard !metricsToAdd.isEmpty else { return }
        for (entry, recorder) in metricsToAdd {
            guard signalMatcher.matches(
                scope: scope,
                name: name,
                props: props,
                condition: entry.condition,
                sdkName: currentSdkName
            ) else { continue }
            let pointAttributes = buildAttributesToPick(from: signalAttributes, entry: entry)
            let signalKind = scope == .traces ? "span" : "log"
            let signalName = name ?? "?"
            switch entry.target {
            case .name:
                PulseLogger.log("Metric derived: \(entry.name) <- \(signalKind) \"\(signalName)\"")
                recorder(name ?? "", nil, pointAttributes)
            case .attribute(let attrCondition, let addPropNameAsSuffix):
                for (attrKey, attrValue) in props {
                    let keyMatches = attrCondition.props.contains { prop in
                        regexCache.matches(string: attrKey, pattern: prop.name)
                    }
                    if keyMatches {
                        let suffix = addPropNameAsSuffix ? attrKey : nil
                        let metricName = suffix.map { "\(entry.name).\($0)" } ?? entry.name
                        PulseLogger.log("Metric derived: \(metricName) <- \(signalKind) \"\(signalName)\" (attr: \(attrKey))")
                        recorder(attrValue, suffix, pointAttributes)
                    }
                }
            }
        }
    }

    // MARK: - SampledSpanExporter

    public final class SampledSpanExporter: SpanExporter {
        private weak var parent: PulseSamplingSignalProcessors?
        private let delegateExporter: SpanExporter

        init(parent: PulseSamplingSignalProcessors, delegateExporter: SpanExporter) {
            self.parent = parent
            self.delegateExporter = delegateExporter
        }

        private var attributesToDrop: [PulseAttributesToDropEntry] {
            parent?.getDroppedAttributesConfig(scope: .traces) ?? []
        }

        private var attributesToAdd: [PulseAttributesToAddEntry] {
            parent?.getAddedAttributesConfig(scope: .traces) ?? []
        }

        private var metricsToAdd: [(PulseMetricsToAddEntry, DataRecorder)] {
            parent?.getMetricsToAddConfig(scope: .traces) ?? []
        }

        public func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
            guard let parent = parent else { return delegateExporter.export(spans: spans, explicitTimeout: explicitTimeout) }
            // Pipeline order per spec: add → metrics → drop → targeted sampling → session sampling
            let filtered = spans.compactMap { span -> SpanData? in
                var currentAttrs = span.attributes
                var result = span
                // 1. Add attributes (enrich)
                if !attributesToAdd.isEmpty {
                    if let added = parent.addAttributes(
                        signalName: span.name,
                        signalAttributes: currentAttrs,
                        scope: .traces,
                        attributesToAdd: attributesToAdd
                    ) {
                        result = spanWithAttributes(result, added)
                        currentAttrs = added
                    }
                }
                // 2. Emit metrics (observe signal before attribute drops)
                parent.recordMetricsForSignal(
                    scope: .traces,
                    name: span.name,
                    props: PulseSamplingSignalProcessors.attributesToMap(result.attributes),
                    signalAttributes: result.attributes,
                    metricsToAdd: metricsToAdd
                )
                // 3. Drop attributes
                if !attributesToDrop.isEmpty {
                    if let dropped = parent.filterAttributes(
                        signalName: span.name,
                        signalAttributes: currentAttrs,
                        scope: .traces,
                        attributesToDrop: attributesToDrop,
                        regexCache: parent.regexCache
                    ) {
                        result = spanWithAttributes(result, dropped)
                        currentAttrs = dropped
                    }
                }
                // 4. Targeted signal sampling / filter (decide export)
                let propsMap = PulseSamplingSignalProcessors.attributesToMap(result.attributes)
                guard parent.shouldExportSignal(
                    scope: .traces,
                    name: span.name,
                    props: propsMap
                ) else { return nil }
                return result
            }
            return delegateExporter.export(spans: filtered, explicitTimeout: explicitTimeout)
        }

        public func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
            delegateExporter.flush(explicitTimeout: explicitTimeout)
        }

        public func shutdown(explicitTimeout: TimeInterval?) {
            delegateExporter.shutdown(explicitTimeout: explicitTimeout)
        }

        private func spanWithAttributes(_ span: SpanData, _ attrs: [String: AttributeValue]) -> SpanData {
            var s = span
            _ = s.settingAttributes(attrs)
            _ = s.settingTotalAttributeCount(attrs.count)
            return s
        }
    }

    // MARK: - SampledLogExporter

    public final class SampledLogExporter: LogRecordExporter {
        private weak var parent: PulseSamplingSignalProcessors?
        private let delegateExporter: LogRecordExporter

        init(parent: PulseSamplingSignalProcessors, delegateExporter: LogRecordExporter) {
            self.parent = parent
            self.delegateExporter = delegateExporter
        }

        private var attributesToDrop: [PulseAttributesToDropEntry] {
            parent?.getDroppedAttributesConfig(scope: .logs) ?? []
        }

        private var attributesToAdd: [PulseAttributesToAddEntry] {
            parent?.getAddedAttributesConfig(scope: .logs) ?? []
        }

        private var metricsToAdd: [(PulseMetricsToAddEntry, DataRecorder)] {
            parent?.getMetricsToAddConfig(scope: .logs) ?? []
        }

        public func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
            guard let parent = parent else {
                return delegateExporter.export(logRecords: logRecords, explicitTimeout: explicitTimeout)
            }
            // Pipeline order per spec: add → metrics → drop → targeted sampling → session sampling
            let filtered = logRecords.compactMap { record -> ReadableLogRecord? in
                let logName = logNameFromRecord(record)
                var currentAttrs = record.attributes
                var result = record
                // 1. Add attributes (enrich)
                if !attributesToAdd.isEmpty {
                    if let added = parent.addAttributes(
                        signalName: logName,
                        signalAttributes: currentAttrs,
                        scope: .logs,
                        attributesToAdd: attributesToAdd
                    ) {
                        result = logRecordWithAttributes(record, added)
                        currentAttrs = added
                    }
                }
                // 2. Emit metrics (observe signal before attribute drops)
                if !metricsToAdd.isEmpty {
                    let propsMap = PulseSamplingSignalProcessors.attributesToMap(result.attributes)
                    parent.recordMetricsForSignal(
                        scope: .logs,
                        name: logName,
                        props: propsMap,
                        signalAttributes: result.attributes,
                        metricsToAdd: metricsToAdd
                    )
                }
                // 3. Drop attributes
                if !attributesToDrop.isEmpty {
                    if let dropped = parent.filterAttributes(
                        signalName: logName,
                        signalAttributes: currentAttrs,
                        scope: .logs,
                        attributesToDrop: attributesToDrop,
                        regexCache: parent.regexCache
                    ) {
                        result = logRecordWithAttributes(record, dropped)
                        currentAttrs = dropped
                    }
                }
                // 4. Targeted signal sampling / filter (decide export)
                let propsMap = PulseSamplingSignalProcessors.attributesToMap(result.attributes)
                guard parent.shouldExportSignal(
                    scope: .logs,
                    name: logName,
                    props: propsMap
                ) else { return nil }
                return result
            }
            return delegateExporter.export(logRecords: filtered, explicitTimeout: explicitTimeout)
        }

        public func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
            delegateExporter.forceFlush(explicitTimeout: explicitTimeout)
        }

        public func shutdown(explicitTimeout: TimeInterval?) {
            delegateExporter.shutdown(explicitTimeout: explicitTimeout)
        }

        private func logNameFromRecord(_ record: ReadableLogRecord) -> String {
            PulseSamplingSignalProcessors.logNameForMatching(record)
        }

        private func logRecordWithAttributes(_ record: ReadableLogRecord, _ attrs: [String: AttributeValue]) -> ReadableLogRecord {
            ReadableLogRecord(
                resource: record.resource,
                instrumentationScopeInfo: record.instrumentationScopeInfo,
                timestamp: record.timestamp,
                observedTimestamp: record.observedTimestamp,
                spanContext: record.spanContext,
                severity: record.severity,
                body: record.body,
                attributes: attrs,
                eventName: record.eventName
            )
        }
    }

    // MARK: - SampledMetricExporter

    public final class SampledMetricExporter: MetricExporter {
        private let delegateExporter: MetricExporter

        init(parent: PulseSamplingSignalProcessors, delegateExporter: MetricExporter) {
            self.delegateExporter = delegateExporter
        }

        public func export(metrics: [MetricData]) -> ExportResult {
            // Metrics are only derived (metricsToAdd) currently — no sampling, pass through
            return delegateExporter.export(metrics: metrics)
        }

        public func flush() -> ExportResult {
            delegateExporter.flush()
        }

        public func shutdown() -> ExportResult {
            delegateExporter.shutdown()
        }

        public func getAggregationTemporality(for instrument: InstrumentType) -> AggregationTemporality {
            delegateExporter.getAggregationTemporality(for: instrument)
        }

        public func getDefaultAggregation(for instrument: InstrumentType) -> Aggregation {
            delegateExporter.getDefaultAggregation(for: instrument)
        }
    }

    // MARK: - Factory methods

    public func makeSampledSpanExporter(delegateExporter: SpanExporter) -> SampledSpanExporter {
        SampledSpanExporter(parent: self, delegateExporter: delegateExporter)
    }

    public func makeSampledLogExporter(delegateExporter: LogRecordExporter) -> SampledLogExporter {
        SampledLogExporter(parent: self, delegateExporter: delegateExporter)
    }

    public func makeSampledMetricExporter(delegateExporter: MetricExporter) -> SampledMetricExporter {
        SampledMetricExporter(parent: self, delegateExporter: delegateExporter)
    }

    // MARK: - getEnabledFeatures

    /// Returns features with sessionSampleRate == 1 and currentSdk in sdks (LLD §8, Batch 4).
    public func getEnabledFeatures() -> [PulseFeatureName] {
        let configFeatures = sdkConfig.features
        return configFeatures
            .filter { $0.sdks.contains(currentSdkName) && $0.sessionSampleRate == 1 }
            .map(\.featureName)
    }

    // MARK: - Session sampling (critical events)

    private func sampleSpansInSession(_ spans: [SpanData]) -> [SpanData] {
        sampleInSession(spans) { span in
            (span.name, Self.attributesToMap(span.attributes))
        }
    }

    private func sampleLogsInSession(_ logs: [ReadableLogRecord]) -> [ReadableLogRecord] {
        sampleInSession(logs) { record in
            let name = Self.logNameForMatching(record)
            return (name, Self.attributesToMap(record.attributes))
        }
    }

    /// Log name for sampling/filter matching. Always use body (bodyValue?.asString()).
    /// Extract string from AttributeValue; .description returns debug format, not the actual value.
    static func logNameForMatching(_ record: ReadableLogRecord) -> String {
        guard let body = record.body else { return "" }
        if case .string(let s) = body { return s }
        return String(describing: body)
    }

    private func sampleMetricsInSession(_ metrics: [MetricData]) -> [MetricData] {
        sampleInSession(metrics) { ($0.name, [:]) }
    }

    private func sampleInSession<M>(_ signals: [M], signalValues: (M) -> (String, [String: Any?])) -> [M] {
        if sessionSamplingDecision.shouldSampleThisSession {
            return signals
        }
        let alwaysSendConditions = sdkConfig.sampling.signalsToSample
            .filter { $0.sampleRate >= 1 }
            .map(\.condition)
        guard !alwaysSendConditions.isEmpty else {
            return []
        }
        return signals.filter { signal in
            let (name, props) = signalValues(signal)
            let scope = scopeForM(signal)
            return alwaysSendConditions.contains { condition in
                signalMatcher.matches(
                    scope: scope,
                    name: name.isEmpty ? nil : name,
                    props: props,
                    condition: condition,
                    sdkName: currentSdkName
                )
            }
        }
    }

    private func scopeForM<M>(_ signal: M) -> PulseSignalScope {
        if signal is SpanData { return .traces }
        if signal is ReadableLogRecord { return .logs }
        return .metrics
    }

    // MARK: - Targeted signal sampling (signalsToSample)

    /// Returns true if the signal should be exported.
    /// Order: targeted (signalsToSample) first, then session sampling as fallback.
    /// When signalsToSample has a matching entry: use its sampleRate.
    /// When no match (or signalsToSample empty): session sampling decides (keep all if sampled, else drop).
    private func shouldExportSignal(
        scope: PulseSignalScope,
        name: String?,
        props: [String: Any?]
    ) -> Bool {
        let signalsToSample = sdkConfig.sampling.signalsToSample
        if signalsToSample.isEmpty {
            return sessionSamplingDecision.shouldSampleThisSession
        }
        return shouldExportBySignalsToSample(scope: scope, name: name, props: props)
    }

    private func shouldExportBySignalsToSample(
        scope: PulseSignalScope,
        name: String?,
        props: [String: Any?]
    ) -> Bool {
        for entry in sdkConfig.sampling.signalsToSample {
            guard signalMatcher.matches(
                scope: scope,
                name: name,
                props: props,
                condition: entry.condition,
                sdkName: currentSdkName
            ) else { continue }
            if entry.sampleRate <= 0 { return false }
            if entry.sampleRate >= 1 { return true }
            return sessionSamplingDecision.sessionRandomValue < entry.sampleRate
        }
        // No match: session sampling applies (step 5)
        return sessionSamplingDecision.shouldSampleThisSession
    }

    // MARK: - Attribute drop

    /// Drops attributes whose names are in matching entries' values. Condition matching is same as attributesToAdd.
    private func filterAttributes(
        signalName: String,
        signalAttributes: [String: AttributeValue],
        scope: PulseSignalScope,
        attributesToDrop: [PulseAttributesToDropEntry],
        regexCache: SamplingRegexCache
    ) -> [String: AttributeValue]? {
        guard !attributesToDrop.isEmpty else { return nil }
        let propsMap = attributesToMap(signalAttributes)
        let matching = attributesToDrop.filter {
            regexCache.matches(string: signalName, pattern: $0.condition.name)
        }
        let entriesThatMatch = matching.filter { entry in
            signalMatcher.matches(
                scope: scope,
                name: signalName,
                props: propsMap,
                condition: entry.condition,
                sdkName: currentSdkName
            )
        }
        // Collect attribute names to drop; entries with empty values contribute nothing
        let keysToDrop = Set(entriesThatMatch.flatMap(\.values).filter { !$0.isEmpty })
        guard !keysToDrop.isEmpty, keysToDrop.contains(where: { signalAttributes[$0] != nil }) else {
            return nil
        }
        var newAttrs = signalAttributes
        for key in keysToDrop {
            newAttrs.removeValue(forKey: key)
        }
        return newAttrs
    }

    // MARK: - Attribute add

    private func addAttributes(
        signalName: String,
        signalAttributes: [String: AttributeValue],
        scope: PulseSignalScope,
        attributesToAdd: [PulseAttributesToAddEntry]
    ) -> [String: AttributeValue]? {
        let matching = attributesToAdd.filter {
            regexCache.matches(string: signalName, pattern: $0.condition.name)
        }
        guard !matching.isEmpty else { return nil }
        let propsMap = attributesToMap(signalAttributes)
        let entriesThatMatch = matching.filter { entry in
            signalMatcher.matches(
                scope: scope,
                name: signalName,
                props: propsMap,
                condition: entry.condition,
                sdkName: currentSdkName
            )
        }
        guard !entriesThatMatch.isEmpty else { return nil }
        let toAdd = entriesThatMatch.flatMap(\.values)
        guard !toAdd.isEmpty else { return nil }
        var newAttrs = signalAttributes
        var addedKeys: [String] = []
        for attr in toAdd {
            if let av = attributeValueFrom(attr) {
                newAttrs[attr.name] = av
                addedKeys.append(attr.name)
            }
        }
        return newAttrs
    }

    private func attributeValueFrom(_ av: PulseAttributeValue) -> AttributeValue? {
        switch av.type {
        case .string: return .string(av.value)
        case .boolean: return Bool(av.value.lowercased()) == true ? .bool(true) : .bool(false)
        case .long: return Int64(av.value).map { .int(Int($0)) }
        case .double: return Double(av.value).map { .double($0) }
        case .string_array:
            let arr = av.value.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            return .array(AttributeArray(values: arr.map { .string($0) }))
        case .boolean_array:
            let arr = av.value.split(separator: ",").compactMap { Bool($0.trimmingCharacters(in: .whitespaces)) }
            return .array(AttributeArray(values: arr.map { .bool($0) }))
        case .long_array:
            let arr = av.value.split(separator: ",").compactMap { Int64($0.trimmingCharacters(in: .whitespaces)) }
            return .array(AttributeArray(values: arr.map { .int(Int($0)) }))
        case .double_array:
            let arr = av.value.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
            return .array(AttributeArray(values: arr.map { .double($0) }))
        }
    }

    /// Converts AttributeValue to String for regex matching. Reuses attributesToMap to avoid
    /// duplicating the AttributeValue switch (which can break when OpenTelemetry adds new cases).
    private func stringFromAttributeValue(_ av: AttributeValue) -> String {
        let mapped = attributesToMap(["_k": av])
        guard let val = mapped["_k"] else { return "" }
        if let s = val as? String { return s }
        if let b = val as? Bool { return String(b) }
        if let i = val as? Int { return String(i) }
        if let d = val as? Double { return String(d) }
        if let arr = val as? [String] { return arr.joined(separator: ",") }
        if let arr = val as? [Bool] { return arr.map { String($0) }.joined(separator: ",") }
        if let arr = val as? [Int] { return arr.map { String($0) }.joined(separator: ",") }
        if let arr = val as? [Double] { return arr.map { String($0) }.joined(separator: ",") }
        return String(describing: val)
    }

    private func attributesToMap(_ attrs: [String: AttributeValue]) -> [String: Any?] {
        attrs.mapValues { av in
            switch av {
            case .string(let s): return s
            case .bool(let b): return b
            case .int(let i): return i
            case .double(let d): return d
            case .stringArray(let a): return a
            case .boolArray(let a): return a
            case .intArray(let a): return a
            case .doubleArray(let a): return a
            case .array(let a): return a.values.map { $0.description }
            case .set(let s): return Array(s.labels)
            }
        }
    }

    // MARK: - Attribute helpers (static for exporters)

    internal static func attributesToMap(_ attrs: [String: AttributeValue]) -> [String: Any?] {
        attrs.mapValues { av in
            switch av {
            case .string(let s): return s
            case .bool(let b): return b
            case .int(let i): return i
            case .double(let d): return d
            case .stringArray(let a): return a
            case .boolArray(let a): return a
            case .intArray(let a): return a
            case .doubleArray(let a): return a
            case .array(let a): return a.values.map { $0.description }
            case .set(let s): return Array(s.labels)
            }
        }
    }
}

// MARK: - SamplingRegexCache (thread-safe, shared for name matching)

/// Normalizes UI-generated "contains" patterns. The UI may produce
/// `^\.\*event\*\.$` (literal .*...*.) when the user intends "contains event".
/// Converts to `.*event.*`. All other patterns (exact match, correct regex) pass through unchanged.
private func normalizedSignalNamePattern(_ pattern: String) -> String {
    let prefix = "^\\.\\*"   // ^\.\* in regex = literal ".*"
    let suffix = "\\*\\.$"   // \*\.$ in regex = literal "*."
    guard pattern.hasPrefix(prefix), pattern.hasSuffix(suffix), pattern.count > prefix.count + suffix.count else {
        return pattern
    }
    let startIdx = pattern.index(pattern.startIndex, offsetBy: prefix.count)
    let endIdx = pattern.index(pattern.endIndex, offsetBy: -suffix.count)
    let content = String(pattern[startIdx..<endIdx])
    return ".*" + NSRegularExpression.escapedPattern(for: content) + ".*"
}

private final class SamplingRegexCache {
    private var cache: [String: NSRegularExpression] = [:]
    private let lock = NSLock()

    func matches(string: String, pattern: String) -> Bool {
        let effectivePattern = normalizedSignalNamePattern(pattern)
        let regex: NSRegularExpression? = lock.withLock {
            if let cached = cache[effectivePattern] { return cached }
            guard let re = try? NSRegularExpression(pattern: effectivePattern) else { return nil }
            cache[effectivePattern] = re
            return re
        }
        guard let regex = regex else { return false }
        let nsString = string as NSString
        let fullRange = NSRange(location: 0, length: nsString.length)
        guard let match = regex.firstMatch(in: string, options: [], range: fullRange) else {
            return false
        }
        return match.range == fullRange
    }
}

private extension NSLock {
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}
