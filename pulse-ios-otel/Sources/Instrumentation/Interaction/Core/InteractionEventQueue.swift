/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import Combine

/// Thread-safe event queue for interaction events
/// Uses Combine PassthroughSubject for broadcast pattern (similar to Kotlin SharedFlow)
/// This allows multiple trackers to process events in parallel
internal final class InteractionEventQueue {
    /// Subject for local events (broadcasts to all subscribers)
    private let localEventsSubject = PassthroughSubject<InteractionLocalEvent, Never>()
    
    /// Subject for marker events (broadcasts to all subscribers)
    private let markerEventsSubject = PassthroughSubject<InteractionLocalEvent, Never>()
    
    /// Publisher for local events (multiple subscribers supported)
    let localEventsPublisher: AnyPublisher<InteractionLocalEvent, Never>
    
    /// Publisher for marker events (multiple subscribers supported)
    let markerEventsPublisher: AnyPublisher<InteractionLocalEvent, Never>
    
    /// Serial queue for thread-safe event emission
    private let serialQueue = DispatchQueue(label: "io.opentelemetry.interaction.event.queue", qos: .utility)
    
    init() {
        // Create publishers that broadcast to all subscribers
        localEventsPublisher = localEventsSubject.eraseToAnyPublisher()
        markerEventsPublisher = markerEventsSubject.eraseToAnyPublisher()
    }
    
    /// Add an event to the queue (thread-safe, broadcasts to all subscribers)
    func addEvent(_ event: InteractionLocalEvent) {
        serialQueue.async { [weak self] in
            self?.localEventsSubject.send(event)
        }
    }
    
    /// Add a marker event to the queue (thread-safe, broadcasts to all subscribers)
    func addMarkerEvent(_ event: InteractionLocalEvent) {
        serialQueue.async { [weak self] in
            self?.markerEventsSubject.send(event)
        }
    }
    
    /// Finish the streams (for cleanup)
    func finish() {
        serialQueue.async { [weak self] in
            self?.localEventsSubject.send(completion: .finished)
            self?.markerEventsSubject.send(completion: .finished)
        }
    }
}

