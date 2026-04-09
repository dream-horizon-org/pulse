/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests for Phase 3: sanitizeMetricName, createMeter, getMetricsToAddConfig.
 */

import XCTest
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit

final class PulseMetricCreationTests: XCTestCase {

    // MARK: - sanitizeMetricName

    func testSanitizeMetricNameAllowsValidChars() {
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "my_metric.name-1/2"), "my_metric.name-1/2")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "valid"), "valid")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "a"), "a")
    }

    func testSanitizeMetricNameStripsInvalidChars() {
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "my metric"), "my_metric")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "a@b#c"), "a_b_c")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "space and-dot.ok"), "space_and-dot.ok")
    }

    func testSanitizeMetricNameMustStartWithLetter() {
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "123"), "m123")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "_leading"), "m_leading")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: ".leading"), "m.leading")
    }

    func testSanitizeMetricNameTruncatesAt255() {
        let long = String(repeating: "a", count: 300)
        let result = PulseOtelUtils.sanitizeMetricName(name: long)
        XCTAssertEqual(result.count, 255)
        XCTAssertEqual(result, String(repeating: "a", count: 255))
    }

    func testSanitizeMetricNameEmptyAndSingleChar() {
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: ""), "m")
        XCTAssertEqual(PulseOtelUtils.sanitizeMetricName(name: "x"), "x")
    }

    // MARK: - getMetricsToAddConfig filtering

    func testGetMetricsToAddConfigFiltersByScopeAndSdk() {
        let entry = PulseMetricsToAddEntry(
            name: "test_counter",
            target: .name,
            condition: PulseSignalMatchCondition(
                name: ".*",
                props: [],
                scopes: [.traces],
                sdks: [.pulse_ios_swift]
            ),
            type: .counter,
            attributesToPick: []
        )
        let config = makeSdkConfig(metricsToAdd: [entry])
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 }
        )
        let tracesConfig = processors.getMetricsToAddConfig(scope: .traces)
        let logsConfig = processors.getMetricsToAddConfig(scope: .logs)
        XCTAssertEqual(tracesConfig.count, 1)
        XCTAssertEqual(logsConfig.count, 0)
    }

    func testGetMetricsToAddConfigExcludesOtherSdks() {
        let entry = PulseMetricsToAddEntry(
            name: "android_only",
            target: .name,
            condition: PulseSignalMatchCondition(
                name: ".*",
                props: [],
                scopes: [.traces],
                sdks: [.pulse_android_java]
            ),
            type: .counter,
            attributesToPick: []
        )
        let config = makeSdkConfig(metricsToAdd: [entry])
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 }
        )
        let tracesConfig = processors.getMetricsToAddConfig(scope: .traces)
        XCTAssertEqual(tracesConfig.count, 0)
    }

    // MARK: - createMeter with MetricExporterMock

    func testCreateMeterCounterMonotonicLong() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "span_count",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        XCTAssertEqual(config.count, 1)
        let (_, recorder) = config[0]
        recorder("ignored_name", nil, [:])
        recorder(nil as String?, nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let counterMetric = metrics.first { $0.name == "span_count" }
        XCTAssertNotNil(counterMetric)
        if let sumData = counterMetric?.data as? SumData,
           let point = sumData.points.first as? LongPointData {
            XCTAssertEqual(sumData.points.count, 1)
            XCTAssertEqual(point.value, 2)
        } else {
            XCTFail("Expected SumData with LongPointData for counter")
        }
    }

    func testCreateMeterCounterRecordsLong() {
        // Counter always uses LongCounter (add 1 per occurrence)
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "span_count_d",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("name", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "span_count_d" }
        XCTAssertNotNil(m)
        if let sumData = m?.data as? SumData,
           let point = sumData.points.first as? LongPointData {
            XCTAssertEqual(sumData.points.count, 1)
            XCTAssertEqual(point.value, 1)
        }
    }

    func testCreateMeterUpDownCounter() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "up_down",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("event", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        XCTAssertTrue(metrics.contains { $0.name == "up_down" })
    }

    func testCreateMeterGaugeDouble() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "gauge_val",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .gauge(isFraction: true),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("42.5", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "gauge_val" }
        XCTAssertNotNil(m)
        if let gaugeData = m?.data as? GaugeData,
           let point = gaugeData.points.first as? DoublePointData {
            XCTAssertEqual(gaugeData.points.count, 1)
            XCTAssertEqual(point.value, 42.5)
        }
    }

    func testCreateMeterGaugeLong() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "gauge_long",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .gauge(isFraction: false),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("100", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "gauge_long" }
        XCTAssertNotNil(m)
        if let gaugeData = m?.data as? GaugeData,
           let point = gaugeData.points.first as? LongPointData {
            XCTAssertEqual(gaugeData.points.count, 1)
            XCTAssertEqual(point.value, 100)
        }
    }

    func testCreateMeterHistogramWithBuckets() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "hist_buckets",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .histogram(bucket: [1.0, 5.0, 10.0], isFraction: true),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("3.5", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        XCTAssertTrue(metrics.contains { $0.name == "hist_buckets" })
    }

    func testCreateMeterHistogramNoBuckets() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "hist_no_buckets",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .histogram(bucket: nil, isFraction: true),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("7.0", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        XCTAssertTrue(metrics.contains { $0.name == "hist_no_buckets" })
    }

    func testCreateMeterSum() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "sum_val",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .sum(isFraction: true, isMonotonic: false),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("12.5", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "sum_val" }
        XCTAssertNotNil(m)
        if let sumData = m?.data as? SumData,
           let point = sumData.points.first as? DoublePointData {
            XCTAssertEqual(sumData.points.count, 1)
            XCTAssertEqual(point.value, 12.5)
        }
    }

    // MARK: - Phase 4: End-to-end metrics recording via span/log export

    func testSpanExportRecordsMatchingMetrics() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "spans_exported",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: "test\\.span",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "test.span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        XCTAssertEqual(mockSpanExporter.exportedSpans.count, 1)
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "spans_exported" }
        XCTAssertNotNil(m, "Metric should be recorded when matching span is exported")
    }

    func testLogExportRecordsMatchingMetrics() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "logs_exported",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.logs],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockLogExporter = MockLogExporter()
        let sampledExporter = processors.makeSampledLogExporter(delegateExporter: mockLogExporter)
        let logRecord = createTestLogRecord(body: "my_log_event")
        _ = sampledExporter.export(logRecords: [logRecord], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        XCTAssertEqual(mockLogExporter.exportedLogs.count, 1)
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "logs_exported" }
        XCTAssertNotNil(m, "Metric should be recorded when matching log is exported")
    }

    func testSpanExportDoesNotRecordWhenConditionDoesNotMatch() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "only_specific_span",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: "^exact\\.match$",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "other.span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        XCTAssertEqual(mockSpanExporter.exportedSpans.count, 1)
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "only_specific_span" }
        XCTAssertNil(m, "Metric should not be recorded when condition does not match")
    }

    func testLogExportRecordsMetricFromTargetAttribute() {
        let attrMatcher = PulseSignalMatchCondition(
            name: ".*",
            props: [PulseProp(name: "response\\.time", value: nil)],
            scopes: [.logs],
            sdks: [.pulse_ios_swift]
        )
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "log_response_time",
                target: .attribute(
                    condition: attrMatcher,
                    addPropNameAsSuffix: false
                ),
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.logs], sdks: [.pulse_ios_swift]),
                type: .gauge(isFraction: true),
                attributesToPick: []
            ),
        ])
        let mockLogExporter = MockLogExporter()
        let sampledExporter = processors.makeSampledLogExporter(delegateExporter: mockLogExporter)
        let log = createTestLogRecord(body: "api_call", attributes: ["response.time": .double(42.5)])
        _ = sampledExporter.export(logRecords: [log], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "log_response_time" }
        XCTAssertNotNil(m, "Metric should be recorded from log attribute")
        if let gaugeData = m?.data as? GaugeData, let point = gaugeData.points.first as? DoublePointData {
            XCTAssertEqual(point.value, 42.5)
        }
    }

    func testLogExportRecordsMetricWithAddPropNameAsSuffix() {
        let attrMatcher = PulseSignalMatchCondition(
            name: ".*",
            props: [PulseProp(name: "event\\.type", value: nil)],
            scopes: [.logs],
            sdks: [.pulse_ios_swift]
        )
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "log_event_count",
                target: .attribute(
                    condition: attrMatcher,
                    addPropNameAsSuffix: true
                ),
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.logs], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockLogExporter = MockLogExporter()
        let sampledExporter = processors.makeSampledLogExporter(delegateExporter: mockLogExporter)
        let log1 = createTestLogRecord(body: "event", attributes: ["event.type": .string("click")])
        let log2 = createTestLogRecord(body: "event", attributes: ["event.type": .string("scroll")])
        _ = sampledExporter.export(logRecords: [log1, log2], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "log_event_count.event.type" }
        XCTAssertNotNil(m, "Should have metric with event.type suffix for logs")
        if let sumData = m?.data as? SumData, let point = sumData.points.first as? LongPointData {
            XCTAssertEqual(point.value, 2)
        }
    }

    func testSpanExportDoesNotRecordWhenWrongScope() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "logs_only_metric",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.logs],
                    sdks: [.pulse_ios_swift]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "test.span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "logs_only_metric" }
        XCTAssertNil(m, "Metric scoped to logs should not fire for spans")
    }

    func testSpanExportDoesNotRecordWhenWrongSdk() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "android_only_metric",
                target: .name,
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_android_java]
                ),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "test.span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "android_only_metric" }
        XCTAssertNil(m, "Metric scoped to Android SDK should not fire for iOS Swift")
    }

    func testSumMonotonicRecordsAsLongCounter() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "span_sum_monotonic",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .sum(isFraction: false, isMonotonic: true),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("100", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "span_sum_monotonic" }
        XCTAssertNotNil(m)
        if let sumData = m?.data as? SumData, let point = sumData.points.first as? LongPointData {
            XCTAssertEqual(point.value, 100)
        }
    }

    func testCreateMeterHistogramLong() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "hist_long",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .histogram(bucket: [10, 50, 100], isFraction: false),
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("75", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        XCTAssertTrue(metrics.contains { $0.name == "hist_long" })
    }

    func testMultipleMetricsToAddMatchSameSpan() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "span_counter_a",
                target: .name,
                condition: PulseSignalMatchCondition(name: "http.*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
            PulseMetricsToAddEntry(
                name: "span_counter_b",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "http.request")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let a = metrics.first { $0.name == "span_counter_a" }
        let b = metrics.first { $0.name == "span_counter_b" }
        XCTAssertNotNil(a, "First matching entry should record")
        XCTAssertNotNil(b, "Second matching entry should record")
    }

    func testTargetAttributeHistogramFromSpanWithMultipleAttrs() {
        // Target .attribute extracts http.duration; span has multiple attrs (http.method, http.duration)
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "http_duration_hist",
                target: .attribute(
                    condition: PulseSignalMatchCondition(
                        name: ".*",
                        props: [PulseProp(name: "http\\.duration", value: nil)],
                        scopes: [.traces],
                        sdks: [.pulse_ios_swift]
                    ),
                    addPropNameAsSuffix: false
                ),
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .histogram(bucket: nil, isFraction: true),
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(
            name: "request",
            attributes: [
                "http.method": .string("GET"),
                "http.duration": .double(150.0),
            ]
        )
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "http_duration_hist" }
        XCTAssertNotNil(m, "Target attribute histogram from span with multiple attrs")
    }

    func testConditionMatchByPropsRecordsMetric() {
        // Entry condition requires span to have http.method (value nil = match any)
        let conditionWithProp = PulseSignalMatchCondition(
            name: ".*",
            props: [PulseProp(name: "http\\.method", value: nil)],
            scopes: [.traces],
            sdks: [.pulse_ios_swift]
        )
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "http_method_counter",
                target: .name,
                condition: conditionWithProp,
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "request", attributes: ["http.method": .string("GET")])
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "http_method_counter" }
        XCTAssertNotNil(m, "Metric should record when condition matches by props (http.method present)")
    }

    func testConditionMatchByPropsWithValueRegex() {
        // Entry condition requires http.method=GET (regex match)
        let conditionWithProp = PulseSignalMatchCondition(
            name: ".*",
            props: [PulseProp(name: "http\\.method", value: "GET")],
            scopes: [.traces],
            sdks: [.pulse_ios_swift]
        )
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "get_request_count",
                target: .name,
                condition: conditionWithProp,
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "request", attributes: ["http.method": .string("GET")])
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "get_request_count" }
        XCTAssertNotNil(m, "Metric should record when condition matches by props (http.method=GET)")
    }

    func testMetricsDerivedBeforeSessionSampling() {
        // Per spec: add → metrics → drop → targeted → session. Metrics run before session filtering,
        // so metrics are derived from all spans even when session sampling drops the span for export.
        let mock = MetricExporterMock()
        let provider = MeterProviderSdk.builder()
            .registerMetricReader(
                reader: PeriodicMetricReaderBuilder(exporter: mock)
                    .setInterval(timeInterval: 1)
                    .build()
            )
            .registerView(
                selector: InstrumentSelector.builder().setInstrument(name: ".*").build(),
                view: View.builder().build()
            )
            .build()
        let config = makeSdkConfig(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "metric_when_sampled",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOffSessionParser(),
            randomGenerator: { 0.5 },
            meterProviderForMetricsToAdd: provider
        )
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "test.span")
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        XCTAssertEqual(mockSpanExporter.exportedSpans.count, 0, "Span should be dropped when session not sampled")
        let m = mock.exportedMetrics.first { $0.name == "metric_when_sampled" }
        XCTAssertNotNil(m, "Metric is derived before session filtering (add → metrics → drop → targeted → session)")
    }

    // MARK: - Phase 5: addPropNameAsSuffix and attributesToPick

    func testAddPropNameAsSuffixUsesAttrKeyAsSuffixSingleMatchedKey() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "http_method_count",
                target: .attribute(
                    condition: PulseSignalMatchCondition(
                        name: ".*",
                        props: [PulseProp(name: "http\\.method", value: nil)],
                        scopes: [.traces],
                        sdks: [.pulse_ios_swift]
                    ),
                    addPropNameAsSuffix: true
                ),
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        // Export two spans with different http.method values (GET, POST) - both aggregate into same metric
        let span1 = createTestSpan(name: "req", attributes: ["http.method": .string("GET")])
        let span2 = createTestSpan(name: "req", attributes: ["http.method": .string("POST")])
        _ = sampledExporter.export(spans: [span1, span2], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        // Suffix = attr KEY (http.method), not value. One metric for the key, value 2 (both spans).
        let m = metrics.first { $0.name == "http_method_count.http.method" }
        XCTAssertNotNil(m, "Should have metric with http.method suffix (attr key, not value)")
        if let sumData = m?.data as? SumData, let point = sumData.points.first as? LongPointData {
            XCTAssertEqual(point.value, 2, "GET + POST both contribute to same metric")
        }
    }

    func testAddPropNameAsSuffixCreatesSeparateMetricsPerAttrKey() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "http_count",
                target: .attribute(
                    condition: PulseSignalMatchCondition(
                        name: ".*",
                        props: [
                            PulseProp(name: "http\\.method", value: nil),
                            PulseProp(name: "http\\.status_code", value: nil),
                        ],
                        scopes: [.traces],
                        sdks: [.pulse_ios_swift]
                    ),
                    addPropNameAsSuffix: true
                ),
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "req", attributes: [
            "http.method": .string("GET"),
            "http.status_code": .string("200"),
        ])
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let names = metrics.map { $0.name }.sorted()
        XCTAssertEqual(names, ["http_count.http.method", "http_count.http.status_code"], "One metric per attr key")
        metrics.forEach { metric in
            if let sumData = metric.data as? SumData, let point = sumData.points.first as? LongPointData {
                XCTAssertEqual(point.value, 1)
            }
        }
    }

    func testAddPropNameAsSuffixFalseUsesBaseName() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "http_duration",
                target: .attribute(
                    condition: PulseSignalMatchCondition(
                        name: ".*",
                        props: [PulseProp(name: "http\\.duration", value: nil)],
                        scopes: [.traces],
                        sdks: [.pulse_ios_swift]
                    ),
                    addPropNameAsSuffix: false
                ),
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .histogram(bucket: nil, isFraction: true),
                attributesToPick: []
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "req", attributes: ["http.duration": .double(150.0)])
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "http_duration" }
        XCTAssertNotNil(m, "Should use base name when addPropNameAsSuffix is false")
    }

    func testAttributesToPickAttachesAttributesToMetricPoint() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "span_with_method",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: [
                    PulseSignalMatchCondition(
                        name: ".*",
                        props: [PulseProp(name: "http\\.method", value: nil)],
                        scopes: [.traces],
                        sdks: [.pulse_ios_swift]
                    ),
                ]
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(name: "req", attributes: ["http.method": .string("GET")])
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "span_with_method" }
        XCTAssertNotNil(m)
        if let sumData = m?.data as? SumData,
           let point = sumData.points.first as? LongPointData {
            let methodAttr = point.attributes["http.method"]
            XCTAssertNotNil(methodAttr, "Metric point should have http.method from attributesToPick")
            if case .string(let s) = methodAttr {
                XCTAssertEqual(s, "GET")
            }
        }
    }

    func testAttributesToPickEmptyUsesNoAttributes() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "simple_counter",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: []
            ),
        ])
        let config = processors.getMetricsToAddConfig(scope: .traces)
        let (_, recorder) = config[0]
        recorder("span_name", nil, [:])
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "simple_counter" }
        XCTAssertNotNil(m)
        if let sumData = m?.data as? SumData,
           let point = sumData.points.first as? LongPointData {
            XCTAssertTrue(point.attributes.isEmpty, "Empty attributesToPick should yield no point attributes")
        }
    }

    func testAttributesToPickMultipleMatches() {
        let (processors, mock, provider) = makeProcessorsWithMockExporter(metricsToAdd: [
            PulseMetricsToAddEntry(
                name: "multi_attr",
                target: .name,
                condition: PulseSignalMatchCondition(name: ".*", props: [], scopes: [.traces], sdks: [.pulse_ios_swift]),
                type: .counter,
                attributesToPick: [
                    PulseSignalMatchCondition(
                        name: ".*",
                        props: [
                            PulseProp(name: "http\\.method", value: nil),
                            PulseProp(name: "http\\.status_code", value: nil),
                        ],
                        scopes: [.traces],
                        sdks: [.pulse_ios_swift]
                    ),
                ]
            ),
        ])
        let mockSpanExporter = MockSpanExporter()
        let sampledExporter = processors.makeSampledSpanExporter(delegateExporter: mockSpanExporter)
        let span = createTestSpan(
            name: "req",
            attributes: [
                "http.method": .string("POST"),
                "http.status_code": .int(200),
            ]
        )
        _ = sampledExporter.export(spans: [span], explicitTimeout: nil as TimeInterval?)
        _ = provider.forceFlush()
        let metrics = mock.exportedMetrics
        let m = metrics.first { $0.name == "multi_attr" }
        XCTAssertNotNil(m)
        if let sumData = m?.data as? SumData,
           let point = sumData.points.first as? LongPointData {
            XCTAssertNotNil(point.attributes["http.method"])
            XCTAssertNotNil(point.attributes["http.status_code"])
        }
    }

    // MARK: - Helpers

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

    private func createTestLogRecord(body: String = "test", attributes: [String: AttributeValue] = [:]) -> ReadableLogRecord {
        ReadableLogRecord(
            resource: Resource(attributes: [:]),
            instrumentationScopeInfo: InstrumentationScopeInfo(name: "test"),
            timestamp: Date(),
            body: .string(body),
            attributes: attributes
        )
    }

    private func makeSdkConfig(metricsToAdd: [PulseMetricsToAddEntry] = []) -> PulseSdkConfig {
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
                metricsToAdd: metricsToAdd,
            ),
            interaction: PulseInteractionConfig(
                collectorUrl: "https://interaction",
                configUrl: "https://config",
                beforeInitQueueSize: 100
            ),
            features: []
        )
    }

    private func makeProcessorsWithMockExporter(
        metricsToAdd: [PulseMetricsToAddEntry]
    ) -> (PulseSamplingSignalProcessors, MetricExporterMock, MeterProviderSdk) {
        let mock = MetricExporterMock()
        let provider = MeterProviderSdk.builder()
            .registerMetricReader(
                reader: PeriodicMetricReaderBuilder(exporter: mock)
                    .setInterval(timeInterval: 1)
                    .build()
            )
            .registerView(
                selector: InstrumentSelector.builder().setInstrument(name: ".*").build(),
                view: View.builder().build()
            )
            .build()
        let config = makeSdkConfig(metricsToAdd: metricsToAdd)
        let processors = PulseSamplingSignalProcessors(
            sdkConfig: config,
            currentSdkName: .pulse_ios_swift,
            sessionParser: AlwaysOnSessionParser(),
            randomGenerator: { 0 },
            meterProviderForMetricsToAdd: provider
        )
        return (processors, mock, provider)
    }

}

// MARK: - MockSpanExporter / MockLogExporter (for Phase 4 E2E tests)

private final class MockSpanExporter: SpanExporter {
    var exportedSpans: [SpanData] = []

    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        exportedSpans.append(contentsOf: spans)
        return .success
    }

    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode { .success }
    func shutdown(explicitTimeout: TimeInterval?) {}
}

private final class MockLogExporter: LogRecordExporter {
    var exportedLogs: [ReadableLogRecord] = []

    func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
        exportedLogs.append(contentsOf: logRecords)
        return .success
    }

    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult { .success }
    func shutdown(explicitTimeout: TimeInterval?) {}
}

// MARK: - MetricExporterMock

private final class MetricExporterMock: MetricExporter {
    var exportedMetrics: [MetricData] = []

    func export(metrics: [MetricData]) -> ExportResult {
        exportedMetrics.append(contentsOf: metrics)
        return .success
    }

    func flush() -> ExportResult { .success }
    func shutdown() -> ExportResult { .success }
    func getAggregationTemporality(for instrument: InstrumentType) -> AggregationTemporality { .cumulative }
    func getDefaultAggregation(for instrument: InstrumentType) -> Aggregation { SumAggregation() }
}
