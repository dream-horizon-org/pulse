/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Tracks app startup time from SDK initialization to first screen appearance.
internal class AppStartupTimer {
    static let shared = AppStartupTimer()
    
    private let lock = NSLock()
    
    /// Timestamp captured when start() is called (SDK initialization)
    private var startTimestamp: Date?
    
    /// The app start span
    private var appStartSpan: Span?
    
    /// Whether the first screen has appeared (span has ended)
    private var hasEnded: Bool = false
    
    private init() {}
    
    /// Start the app startup timer. Called during SDK initialization.
    /// Creates an "AppStart" span with start_type = "cold"
    func start(tracer: Tracer) {
        lock.lock()
        defer { lock.unlock() }
        
        // Guard against double-start
        guard appStartSpan == nil else { return }
        
        startTimestamp = Date()
        
        let span = tracer.spanBuilder(spanName: "AppStart")
            .setAttribute(key: PulseAttributes.startType, value: "cold")
            .setAttribute(key: PulseAttributes.pulseType, value: PulseAttributes.PulseTypeValues.appStart)
            .startSpan()
        
        appStartSpan = span
    }
    
    /// End the app startup span. Called when first screen appears.
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
}
