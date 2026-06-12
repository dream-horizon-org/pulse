/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

#if canImport(Darwin)
import Darwin.sys.sysctl
#endif

internal class AppStartupTimer {
    static let shared = AppStartupTimer()

    private let lock = NSLock()

    /// OS process-launch time on supported platforms; SDK-init time as fallback.
    private let firstPossibleTimestampInNano: Date
    private let startAnchorSource: String

    /// Held across `end()` so `reportFullyDrawn()` can build the span later.
    private var tracer: Tracer?

    private var appStartSpan: Span?

    /// Whether the first screen has appeared (span has ended)
    private var hasEnded: Bool = false
    private var fullyDrawnReported: Bool = false

    private init() {
        let anchor = AppStartupTimer.resolveStartAnchor()
        self.firstPossibleTimestampInNano = anchor.timestamp
        self.startAnchorSource = anchor.source
    }

    /// Reads process-start via `sysctl(KERN_PROC_PID)`; falls back to `Date()` on failure or
    /// out-of-range deltas (future timestamp or more than 10 minutes in the past).
    private static func resolveStartAnchor() -> (timestamp: Date, source: String) {
        #if canImport(Darwin)
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        guard sysctl(&mib, UInt32(mib.count), &info, &size, nil, 0) == 0 else {
            print("[Pulse AppStart] sysctl failed (errno=\(errno)); using SDK-init time")
            return (Date(), PulseAttributes.StartAnchorValues.sdkInit)
        }
        let tv = info.kp_proc.p_starttime
        let epochSeconds = Double(tv.tv_sec) + Double(tv.tv_usec) / 1_000_000.0
        let processStart = Date(timeIntervalSince1970: epochSeconds)
        let deltaSec = Date().timeIntervalSince(processStart)
        if deltaSec < 0 || deltaSec > 600 {
            print("[Pulse AppStart] process-start delta out of range (\(deltaSec)s); using SDK-init time")
            return (Date(), PulseAttributes.StartAnchorValues.sdkInit)
        }
        return (processStart, PulseAttributes.StartAnchorValues.os)
        #else
        return (Date(), PulseAttributes.StartAnchorValues.sdkInit)
        #endif
    }

    func start(tracer: Tracer) {
        lock.lock()
        defer { lock.unlock() }

        // Guard against double-start
        guard appStartSpan == nil else { return }
        self.tracer = tracer
        appStartSpan = tracer.spanBuilder(spanName: "AppStart")
            .setStartTime(time: firstPossibleTimestampInNano)
            .setAttribute(key: PulseAttributes.startType, value: "cold")
            .setAttribute(key: PulseAttributes.pulseType, value: PulseAttributes.PulseTypeValues.appStart)
            .setAttribute(key: PulseAttributes.startAnchor, value: startAnchorSource)
            .startSpan()
    }

    /// Ends the app startup span. No-op if already ended.
    func end() {
        lock.lock()
        defer { lock.unlock() }

        guard let span = appStartSpan, !hasEnded else { return }

        hasEnded = true
        span.end()
        appStartSpan = nil
    }

    /// Check if app start tracking is in progress
    var isTracking: Bool {
        lock.lock()
        defer { lock.unlock() }
        return appStartSpan != nil && !hasEnded
    }

    /// Emits a one-shot AppInteractive span spanning OS process-start → now.
    func reportFullyDrawn() {
        lock.lock()
        guard !fullyDrawnReported else {
            lock.unlock()
            return
        }
        fullyDrawnReported = true
        let tracer = self.tracer
        lock.unlock()

        guard let tracer = tracer else { return }
        let span = tracer.spanBuilder(spanName: "AppInteractive")
            .setStartTime(time: firstPossibleTimestampInNano)
            .setAttribute(key: PulseAttributes.startAnchor, value: startAnchorSource)
            .startSpan()
        span.end(time: Date())
    }
}
