/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit
import InMemoryExporter

/// Ensures each completed interaction (`interactionId`) produces at most one span when combined
final class InteractionInstrumentationTests: XCTestCase {
    private var spanExporter: InMemoryExporter!
    private var tracerProvider: TracerProviderSdk!
    private var instrumentation: InteractionInstrumentation!

    override func setUp() {
        super.setUp()
        spanExporter = InMemoryExporter()
        tracerProvider = TracerProviderSdk()
        // Export every ended span (root interaction spans are always sampled with default provider, but this avoids flakiness).
        tracerProvider.addSpanProcessor(
            SimpleSpanProcessor(spanExporter: spanExporter).reportingOnlySampled(sampled: false)
        )
        OpenTelemetry.registerTracerProvider(tracerProvider: tracerProvider)
    }

    override func tearDown() {
        instrumentation?.uninstall()
        instrumentation = nil
        spanExporter.reset()
        spanExporter = nil
        tracerProvider?.shutdown()
        tracerProvider = nil
        super.tearDown()
    }

    func testInMemoryExporter_recordsRootSpan() {
        let tracer = tracerProvider.get(instrumentationName: "pulse.otel.interaction", instrumentationVersion: nil)
        let span = tracer.spanBuilder(spanName: "Smoke").setNoParent().startSpan()
        span.end()
        tracerProvider.forceFlush()
        XCTAssertEqual(spanExporter.getFinishedSpanItems().count, 1)
    }

    func testIdenticalSequenceInteractions_EmitsExactlyOneSpanPerInteraction() async throws {
        let events = [
            InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
            InteractionTestUtils.createFakeInteractionEvent(name: "event2"),
        ]
        let configA = try InteractionTestUtils.createFakeInteractionConfig(
            id: 1,
            name: "InteractionA",
            eventSequence: events
        )
        let configB = try InteractionTestUtils.createFakeInteractionConfig(
            id: 2,
            name: "InteractionB",
            eventSequence: events
        )

        var interactionConfiguration = InteractionInstrumentationConfiguration()
        interactionConfiguration.useMockFetcher = true
        interactionConfiguration.mockConfigs = [configA, configB]

        instrumentation = InteractionInstrumentation(configuration: interactionConfiguration)
        await installAndAwaitInteractionReady(instrumentation)

        let manager = instrumentation.managerInstance
        XCTAssertEqual(manager.currentStates.count, 2, "Both trackers should be initialized")

        manager.addEvent(eventName: "event1")
        manager.addEvent(eventName: "event2")

        await waitUntil {
            let states = manager.currentStates
            guard states.count == 2 else { return false }
            return states.allSatisfy { state in
                if case .ongoingMatch(_, _, _, let interaction) = state {
                    return interaction != nil
                }
                return false
            }
        }

        await waitUntil(timeoutSeconds: 5) {
            self.spanExporter.getFinishedSpanItems().count >= 2
        }
        tracerProvider.forceFlush()

        let spans = spanExporter.getFinishedSpanItems()
        let interactionSpans = spans.filter { $0.name == "InteractionA" || $0.name == "InteractionB" }

        XCTAssertEqual(
            interactionSpans.count,
            2,
            "Expected one span per interaction config; without per-interactionId dedupe this is often 3."
        )
        XCTAssertEqual(interactionSpans.filter { $0.name == "InteractionA" }.count, 1)
        XCTAssertEqual(interactionSpans.filter { $0.name == "InteractionB" }.count, 1)
    }

    private func waitUntil(
        timeoutSeconds: TimeInterval = 2,
        _ condition: @escaping () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        while Date() < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: 5_000_000)
        }
    }

    /// Calls `install()` then waits until configs are loaded and the stream consumer is likely running.
    private func installAndAwaitInteractionReady(_ instrumentation: InteractionInstrumentation) async {
        instrumentation.install()
        try? await Task.sleep(nanoseconds: 50_000_000)
        let manager = instrumentation.managerInstance
        await waitUntil(timeoutSeconds: 5) { manager.currentStates.count == 2 }
        for _ in 0 ..< 30 {
            await Task.yield()
        }
    }
}
