/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit
import XCTest

private struct FixedMemory: MemoryMetricsSource {
    let sys: Int64
    let app: Int64
    func systemMemoryUsedBytes() -> Int64? { sys }
    func appMemoryUsedBytes() -> Int64? { app }
}

final class MemorySamplerTests: XCTestCase {
    func testFlush_EmitsMemoryPulseTypeAndArrays() {
        let logExporter = InMemoryLogRecordExporter()
        let loggerProvider = LoggerProviderBuilder()
            .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
            .build()
        let logger = loggerProvider.get(instrumentationScopeName: MemoryInstrumentationConfig.loggerScopeName)

        let sampler = MemorySampler(
            logger: logger,
            flushIntervalMs: 60_000,
            sampleIntervalMs: 5_000,
            metricsSource: FixedMemory(sys: 1_000, app: 500)
        )
        sampler.collectSample()
        sampler.flushSamples()

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1, "One log per flush")
        let log = records[0]
        XCTAssertEqual(log.eventName, PulseAttributes.PulseTypeValues.memory)
        let attrs = log.attributes
        XCTAssertEqual(attrs[PulseAttributes.pulseType], AttributeValue.string(PulseAttributes.PulseTypeValues.memory))
        XCTAssertNotNil(attrs[PulseDeviceAttributes.systemMemoryUtilizationArray])
        XCTAssertNotNil(attrs[PulseDeviceAttributes.appMemoryUtilizationArray])
        XCTAssertNotNil(attrs[PulseDeviceAttributes.systemMemoryTimestampArray])
    }

    func testCollectWhenInactive_DoesNotBuffer() {
        let logExporter = InMemoryLogRecordExporter()
        let loggerProvider = LoggerProviderBuilder()
            .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
            .build()
        let logger = loggerProvider.get(instrumentationScopeName: MemoryInstrumentationConfig.loggerScopeName)

        let sampler = MemorySampler(
            logger: logger,
            flushIntervalMs: 60_000,
            sampleIntervalMs: 5_000,
            metricsSource: FixedMemory(sys: 1, app: 2)
        )
        sampler.setActive(false)
        sampler.collectSample()
        sampler.flushSamples()

        XCTAssertTrue(logExporter.getFinishedLogRecords().isEmpty)
    }
}
