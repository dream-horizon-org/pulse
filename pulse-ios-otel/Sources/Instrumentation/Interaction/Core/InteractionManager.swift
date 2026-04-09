/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import Combine

/// Manages interaction tracking lifecycle
/// Fetches configs, creates trackers, and processes events
public final class InteractionManager {
    private let interactionFetcher: InteractionConfigFetcher
    private var interactionConfigs: [InteractionConfig]?
    private var interactionTrackers: [InteractionEventsTracker]?
    private let eventQueue: InteractionEventQueue
    
    /// Current state of all interaction trackers
    private var interactionTrackerStates: [InteractionRunningStatus] = []
    
    /// Get current interaction tracker states (for processors)
    var currentStates: [InteractionRunningStatus] {
        return interactionTrackerStates
    }
    
    /// Continuation for state updates stream
    private var stateContinuation: AsyncStream<[InteractionRunningStatus]>.Continuation?
    
    /// Stream of interaction tracker states
    let interactionTrackerStatesStream: AsyncStream<[InteractionRunningStatus]>
    
    /// Cancellables for Combine subscriptions (event processing and state observation)
    private var cancellables: Set<AnyCancellable> = []
    
    public init(interactionFetcher: InteractionConfigFetcher) {
        self.interactionFetcher = interactionFetcher
        self.eventQueue = InteractionEventQueue()
        
        // Create state stream
        var continuation: AsyncStream<[InteractionRunningStatus]>.Continuation?
        interactionTrackerStatesStream = AsyncStream { cont in
            continuation = cont
        }
        self.stateContinuation = continuation
    }
    
    func initialize() async {
        do {
            guard let configs = try await interactionFetcher.getConfigs() else {
                return
            }
            
            self.interactionConfigs = configs
            self.interactionTrackers = configs.map { config in
                InteractionEventsTracker(interactionConfig: config)
            }
            
            startEventProcessing()
            
            startStateObservation()
        } catch {
            print("[Pulse] Interaction: Failed to initialize - \(error.localizedDescription). Error: \(error)")
        }
    }
    
    private func startEventProcessing() {
        guard let trackers = interactionTrackers else {
            return
        }
        
        // Process local events - one subscription per tracker (parallel processing)
        for tracker in trackers {
            eventQueue.localEventsPublisher
                .sink { [weak tracker] event in
                    tracker?.checkAndAdd(event: event)
                }
                .store(in: &cancellables)
        }
        
        // Process marker events - one subscription per tracker (parallel processing)
        for tracker in trackers {
            eventQueue.markerEventsPublisher
                .sink { [weak tracker] markerEvent in
                    tracker?.addMarker(markerEvent)
                }
                .store(in: &cancellables)
        }
    }
    
    /// Start observing state changes from all trackers reactively
    /// Uses Combine to reactively combine all tracker states
    private func startStateObservation() {
        guard let trackers = interactionTrackers, !trackers.isEmpty else {
            return
        }
        
        // Subscribe to each tracker's state publisher and combine them reactively
        for (__, tracker) in trackers.enumerated() {
            tracker.statePublisher
                .sink { [weak self] _ in
                    guard let self = self else { return }
                    // When any tracker's state changes, collect all current states
                    let newStates = self.interactionTrackers?.map { $0.currentStatus } ?? []
                    if newStates != self.interactionTrackerStates {
                        self.interactionTrackerStates = newStates
                        self.stateContinuation?.yield(newStates)
                    }
                }
                .store(in: &cancellables)
        }
    }
    
    /// Add event to track for interaction
    /// - Parameters:
    ///   - eventName: Name of the event
    ///   - params: Event parameters
    ///   - eventTimeInNano: Event timestamp in nanoseconds (defaults to current time)
    func addEvent(
        eventName: String,
        params: [String: Any?] = [:],
        eventTimeInNano: Int64? = nil
    ) {
        let timeInNano = eventTimeInNano ?? Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        let props = params.mapValues { value in
            if let stringValue = value as? String {
                return stringValue
            } else if let value = value {
                return String(describing: value)
            } else {
                return ""
            }
        }
        
        let event = InteractionLocalEvent(
            name: eventName,
            timeInNano: timeInNano,
            props: props.isEmpty ? nil : props
        )
        
        eventQueue.addEvent(event)
    }
    
    /// Add marker event (doesn't contribute to matching, appears in timeline)
    /// - Parameters:
    ///   - eventName: Name of the marker event
    ///   - params: Event parameters
    ///   - eventTimeInNano: Event timestamp in nanoseconds (defaults to current time)
    func addMarkerEvent(
        eventName: String,
        params: [String: Any?] = [:],
        eventTimeInNano: Int64? = nil
    ) {
        let timeInNano = eventTimeInNano ?? Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        let props = params.mapValues { value in
            if let stringValue = value as? String {
                return stringValue
            } else if let value = value {
                return String(describing: value)
            } else {
                return ""
            }
        }
        
        let event = InteractionLocalEvent(
            name: eventName,
            timeInNano: timeInNano,
            props: props.isEmpty ? nil : props
        )
        
        eventQueue.addMarkerEvent(event)
    }
    
    /// Cleanup
    deinit {
        eventQueue.finish()
        stateContinuation?.finish()
        cancellables.removeAll()  // Cancel all Combine subscriptions
    }
}

