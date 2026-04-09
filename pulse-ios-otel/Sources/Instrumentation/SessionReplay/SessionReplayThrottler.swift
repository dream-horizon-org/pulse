/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

internal final class SessionReplayThrottler {
    private let throttleDelayMs: Int
    private let queue: DispatchQueue
    private var lastCallTime: TimeInterval = 0
    private var pendingWork: DispatchWorkItem?
    private let lock = NSLock()
    
    init(throttleDelayMs: Int, queue: DispatchQueue) {
        self.throttleDelayMs = throttleDelayMs
        self.queue = queue
    }
    
    func throttle(_ work: @escaping () -> Void) {
        lock.lock()
        defer { lock.unlock() }
        
        let currentTime = Date().timeIntervalSince1970 * 1000
        let timeSinceLastCall = currentTime - lastCallTime
        
        pendingWork?.cancel()
        
        if timeSinceLastCall >= Double(throttleDelayMs) {
            lastCallTime = currentTime
            queue.async(execute: work)
        } else {
            let remainingDelayMs = Double(throttleDelayMs) - timeSinceLastCall
            let workItem = DispatchWorkItem { [weak self] in
                self?.lock.lock()
                self?.lastCallTime = Date().timeIntervalSince1970 * 1000
                self?.lock.unlock()
                work()
            }
            pendingWork = workItem
            queue.asyncAfter(deadline: .now() + remainingDelayMs / 1000.0, execute: workItem)
        }
    }
    
    func cancel() {
        lock.lock()
        defer { lock.unlock() }
        pendingWork?.cancel()
        pendingWork = nil
    }
}
