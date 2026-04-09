/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests for Batch 4: PulseSamplingSignalProcessors (SampledSpanExporter, SampledLogExporter,
 * SampledMetricExporter, getEnabledFeatures).
 */

import XCTest
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit

final class PulseSamplingSignalProcessorsTests: XCTestCase {

    func testGetEnabledFeaturesReturnsFeaturesWithSessionSampleRateOne() {
        let config = makeSdkConfig(
            features: [
                PulseFeatureConfig(featureName: .interaction, sessionSampleRate: 1, sdks: [.pulse_ios_swift]),
                PulseFeatureConfig(featureName: .network_instrumentation, sessionSampleRate: 0.5, sdks: [.pulse_ios_swift]),
                PulseFeatureConfig(featureName: .custom_events, sessionSampleRate: 1, sdks: [.pulse_ios_swift])
            ]
        )
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 1.0 }
        )
        let enabled = processors.getEnabledFeatures()
        XCTAssertEqual(enabled.count, 2)
        XCTAssertTrue(enabled.contains(.interaction))
        XCTAssertTrue(enabled.contains(.custom_events))
        XCTAssertFalse(enabled.contains(.network_instrumentation))
    }

    func testGetEnabledFeaturesExcludesOtherSdks() {
        let config = makeSdkConfig(
            features: [
                PulseFeatureConfig(featureName: .interaction, sessionSampleRate: 1, sdks: [.pulse_android_java])
            ]
        )
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 1.0 }
        )
        let enabled = processors.getEnabledFeatures()
        XCTAssertTrue(enabled.isEmpty)
    }

    func testSampledSpanExporterDelegatesWhenSessionSampled() {
        let mockExporter = MockSpanExporter()
        let config = makeSdkConfig()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(name: "test-span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 1)
        XCTAssertEqual(mockExporter.exportedSpans[0].name, "test-span")
    }

    func testSampledLogExporterDelegatesWhenSessionSampled() {
        let mockExporter = MockLogExporter()
        let config = makeSdkConfig()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 }
        )
        let sampledExporter = processors.makeSampledLogExporter(delegateExporter: mockExporter)
        let record = createTestLogRecord()
        _ = sampledExporter.export(logRecords: [record], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedLogs.count, 1)
    }

    // MARK: - signalsToSample (targeted sampling)

    func testSignalsToSampleSampleRateZeroAlwaysDrops() {
        let config = makeSdkConfigWithSignalsToSample([
            PulseSignalsToSampleEntry(
                condition: PulseSignalMatchCondition(
                    name: "noise_span",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                sampleRate: 0
            )
        ])
        let mockExporter = MockSpanExporter()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0.5 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(name: "noise_span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 0, "sampleRate 0 should always drop")
    }

    func testSignalsToSampleSampleRateOneAlwaysKeeps() {
        let config = makeSdkConfigWithSignalsToSample([
            PulseSignalsToSampleEntry(
                condition: PulseSignalMatchCondition(
                    name: "checkout_complete",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                sampleRate: 1.0
            )
        ])
        let mockExporter = MockSpanExporter()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOffSessionParser(),
            randomGenerator: { 0 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(name: "checkout_complete")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 1, "sampleRate 1.0 should always keep even when session not sampled")
    }

    func testSignalsToSampleFirstMatchWins() {
        let config = makeSdkConfigWithSignalsToSample([
            PulseSignalsToSampleEntry(
                condition: PulseSignalMatchCondition(
                    name: "checkout_complete",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                sampleRate: 1.0
            ),
            PulseSignalsToSampleEntry(
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                sampleRate: 0.0
            )
        ])
        let mockExporter = MockSpanExporter()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOffSessionParser(),
            randomGenerator: { 0 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(name: "checkout_complete")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 1, "First match (checkout_complete rate 1) wins over later .* rate 0")
    }

    func testSignalsToSampleNoMatchUsesSessionSampling() {
        let config = makeSdkConfigWithSignalsToSample([
            PulseSignalsToSampleEntry(
                condition: PulseSignalMatchCondition(
                    name: "only_this",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                sampleRate: 1.0
            )
        ])
        let mockExporter = MockSpanExporter()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(name: "other_span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 1, "No match → session sampling; AlwaysOn keeps it")
    }

    func testSignalsToSampleNoMatchWhenSessionNotSampledDrops() {
        let config = makeSdkConfigWithSignalsToSample([
            PulseSignalsToSampleEntry(
                condition: PulseSignalMatchCondition(
                    name: "only_this",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                sampleRate: 1.0
            )
        ])
        let mockExporter = MockSpanExporter()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOffSessionParser(),
            randomGenerator: { 0.5 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(name: "other_span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 0, "No match + session not sampled (random 0.5 > rate 0) → drop")
    }

    func testSampledSpanExporterDropsAttributesWhenAttributesToDropMatches() {
        let dropCondition = PulseSignalMatchCondition(
            name: ".*",
            props: [],
            scopes: [.traces],
            sdks: [.pulse_ios_swift]
        )
        let attributesToDrop = [
            PulseAttributesToDropEntry(values: ["toDrop"], condition: dropCondition)
        ]
        let config = makeSdkConfigWithAttributesToDrop(attributesToDrop)
        let mockExporter = MockSpanExporter()
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 }
        )
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockExporter)
        let span = createTestSpan(
            name: "test-span",
            attributes: [
                "toDrop": AttributeValue.string("secret"),
                "keep": AttributeValue.string("visible")
            ]
        )
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil)
        XCTAssertEqual(mockExporter.exportedSpans.count, 1)
        let exported = mockExporter.exportedSpans[0]
        XCTAssertNil(exported.attributes["toDrop"], "toDrop should be dropped")
        XCTAssertNotNil(exported.attributes["keep"], "keep should remain")
    }

    // MARK: - Helpers

    private func makeSdkConfig(
        features: [PulseFeatureConfig] = []
    ) -> PulseSdkConfig {
        PulseSdkConfig(
            version: 1,
            description: "test",
            sampling: PulseSamplingConfig(
                default: PulseDefaultSamplingConfig(sessionSampleRate: 0.5),
                rules: [],
                signalsToSample: []
            ),
            signals: PulseSignalConfig(
                scheduleDurationMs: 60_000,
                logsCollectorUrl: "https://logs",
                metricCollectorUrl: "https://metrics",
                spanCollectorUrl: "https://spans",
                customEventCollectorUrl: "https://custom",
                attributesToDrop: [],
                attributesToAdd: [],
                metricsToAdd: []
            ),
            interaction: PulseInteractionConfig(
                collectorUrl: "https://interaction",
                configUrl: "https://config",
                beforeInitQueueSize: 100
            ),
            features: features
        )
    }

    private func makeSdkConfigWithSignalsToSample(_ signalsToSample: [PulseSignalsToSampleEntry]) -> PulseSdkConfig {
        PulseSdkConfig(
            version: 1,
            description: "test",
            sampling: PulseSamplingConfig(
                default: PulseDefaultSamplingConfig(sessionSampleRate: 0.3),
                rules: [],
                signalsToSample: signalsToSample
            ),
            signals: PulseSignalConfig(
                scheduleDurationMs: 60_000,
                logsCollectorUrl: "https://logs",
                metricCollectorUrl: "https://metrics",
                spanCollectorUrl: "https://spans",
                customEventCollectorUrl: "https://custom",
                attributesToDrop: [],
                attributesToAdd: [],
                metricsToAdd: []
            ),
            interaction: PulseInteractionConfig(
                collectorUrl: "https://interaction",
                configUrl: "https://config",
                beforeInitQueueSize: 100
            ),
            features: []
        )
    }

    private func makeSdkConfigWithAttributesToDrop(_ attributesToDrop: [PulseAttributesToDropEntry]) -> PulseSdkConfig {
        PulseSdkConfig(
            version: 1,
            description: "test",
            sampling: PulseSamplingConfig(
                default: PulseDefaultSamplingConfig(sessionSampleRate: 0.5),
                rules: [],
                signalsToSample: []
            ),
            signals: PulseSignalConfig(
                scheduleDurationMs: 60_000,
                logsCollectorUrl: "https://logs",
                metricCollectorUrl: "https://metrics",
                spanCollectorUrl: "https://spans",
                customEventCollectorUrl: "https://custom",
                attributesToDrop: attributesToDrop,
                attributesToAdd: [],
                metricsToAdd: []
            ),
            interaction: PulseInteractionConfig(
                collectorUrl: "https://interaction",
                configUrl: "https://config",
                beforeInitQueueSize: 100
            ),
            features: []
        )
    }

    private func createTestSpan(name: String, attributes: [String: AttributeValue] = [:]) -> SpanData {
        let start = Date()
        let end = start.addingTimeInterval(0.1)
        var spanData = SpanData(
            traceId: TraceId.random(),
            spanId: SpanId.random(),
            name: name,
            kind: SpanKind.internal,
            startTime: start,
            endTime: end
        )
        spanData.settingAttributes(attributes)
        spanData.settingTotalAttributeCount(attributes.count)
        spanData.settingHasEnded(true)
        spanData.settingTotalRecordedEvents(0)
        spanData.settingLinks([])
        spanData.settingTotalRecordedLinks(0)
        spanData.settingStatus(Status.ok)
        return spanData
    }

    private func createTestLogRecord() -> ReadableLogRecord {
        ReadableLogRecord(
            resource: Resource(attributes: [:]),
            instrumentationScopeInfo: InstrumentationScopeInfo(name: "test"),
            timestamp: Date(),
            attributes: [:]
        )
    }
}

private class MockSpanExporter: SpanExporter {
    var exportedSpans: [SpanData] = []

    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        exportedSpans.append(contentsOf: spans)
        return .success
    }

    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode { .success }
    func shutdown(explicitTimeout: TimeInterval?) {}
}

private class MockLogExporter: LogRecordExporter {
    var exportedLogs: [ReadableLogRecord] = []

    func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
        exportedLogs.append(contentsOf: logRecords)
        return .success
    }

    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult { .success }
    func shutdown(explicitTimeout: TimeInterval?) {}
}

// MARK: - PulseSignalSelectExporter tests

final class PulseSignalSelectExporterTests: XCTestCase {
    func testSelectedLogExporterRoutesCustomEventsToCustomExporter() {
        let defaultMock = MockLogExporter()
        let customMock = MockLogExporter()
        let selector = PulseSignalSelectExporter(currentSdkName: .pulse_ios_swift)
        let logMap: [(PulseSignalMatchCondition, LogRecordExporter)] = [
            (PulseSignalMatchCondition.allMatchLogCondition, defaultMock),
            (PulseSignalMatchCondition.customEventLogCondition(
                pulseTypeKey: PulseAttributes.pulseType,
                customEventValue: PulseAttributes.PulseTypeValues.customEvent
            ), customMock),
        ]
        let selectedExporter = selector.makeSelectedLogExporter(logMap: logMap)

        let defaultLog = createLogRecord(attributes: ["other": AttributeValue.string("value")])
        let customEventLog = createLogRecord(attributes: [
            PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.PulseTypeValues.customEvent),
            "name": AttributeValue.string("my_event"),
        ])

        _ = selectedExporter.export(logRecords: [defaultLog, customEventLog], explicitTimeout: nil)

        XCTAssertEqual(defaultMock.exportedLogs.count, 1)
        XCTAssertEqual(customMock.exportedLogs.count, 1)
        if let customAttr = customMock.exportedLogs[0].attributes[PulseAttributes.pulseType],
           case .string(let val) = customAttr {
            XCTAssertEqual(val, PulseAttributes.PulseTypeValues.customEvent)
        } else {
            XCTFail("Expected pulse.type = custom_event on custom exporter")
        }
    }

    private func createLogRecord(attributes: [String: AttributeValue]) -> ReadableLogRecord {
        ReadableLogRecord(
            resource: Resource(attributes: [:]),
            instrumentationScopeInfo: InstrumentationScopeInfo(name: "test"),
            timestamp: Date(),
            attributes: attributes
        )
    }
}
