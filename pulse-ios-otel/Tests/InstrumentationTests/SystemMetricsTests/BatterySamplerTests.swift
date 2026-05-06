/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

#if os(iOS) || os(tvOS)
import Foundation
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit
import XCTest

private final class MockBatterySource: BatterySnapshotSource {
    var samples: [BatterySample?]
    private var index = 0

    init(samples: [BatterySample?]) {
        self.samples = samples
    }

    func readSnapshot() -> BatterySample? {
        guard index < samples.count else { return nil }
        defer { index += 1 }
        return samples[index]
    }
}

final class BatterySamplerTests: XCTestCase {
    func testDedupe_SkipsConsecutiveDuplicate() {
        let logExporter = InMemoryLogRecordExporter()
        let loggerProvider = LoggerProviderBuilder()
            .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
            .build()
        let logger = loggerProvider.get(instrumentationScopeName: BatteryInstrumentationConfig.loggerScopeName)

        let dup = BatterySample(levelPercent: 50, plugged: "battery")
        let source = MockBatterySource(samples: [dup, dup])
        let sampler = BatterySampler(
            logger: logger,
            flushIntervalMs: 60_000,
            sampleIntervalMs: 5_000,
            snapshotSource: source
        )

        DispatchQueue.main.sync {
            sampler.collectSampleOnMain()
            sampler.collectSampleOnMain()
            sampler.flushSamples()
        }

        XCTAssertEqual(logExporter.getFinishedLogRecords().count, 1)
    }

    func testFlush_EmitsBatteryPulseType() {
        let logExporter = InMemoryLogRecordExporter()
        let loggerProvider = LoggerProviderBuilder()
            .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
            .build()
        let logger = loggerProvider.get(instrumentationScopeName: BatteryInstrumentationConfig.loggerScopeName)

        let sample = BatterySample(levelPercent: 99, plugged: "ac")
        let source = MockBatterySource(samples: [sample])
        let sampler = BatterySampler(
            logger: logger,
            flushIntervalMs: 60_000,
            sampleIntervalMs: 5_000,
            snapshotSource: source
        )

        DispatchQueue.main.sync {
            sampler.collectSampleOnMain()
            sampler.flushSamples()
        }

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.first?.eventName, PulseAttributes.PulseTypeValues.battery)
        XCTAssertEqual(
            records.first?.attributes[PulseAttributes.pulseType],
            AttributeValue.string(PulseAttributes.PulseTypeValues.battery)
        )
    }
}
#endif
