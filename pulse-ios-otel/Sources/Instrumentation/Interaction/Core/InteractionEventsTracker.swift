/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import Combine

/// Tracks events for a single interaction configuration
/// Processes events in real-time and maintains state
internal final class InteractionEventsTracker {
    private let interactionConfig: InteractionConfig
    
    /// Current interaction running status
    private var interactionRunningStatus: InteractionRunningStatus = .noOngoingMatch(oldOngoingInteractionRunningStatus: nil) {
        didSet {
            // Emit state changes reactively
            stateSubject.send(interactionRunningStatus)
        }
    }
    
    /// Subject for state changes (reactive updates)
    private let stateSubject = CurrentValueSubject<InteractionRunningStatus, Never>(.noOngoingMatch(oldOngoingInteractionRunningStatus: nil))
    
    /// Publisher for state changes
    var statePublisher: AnyPublisher<InteractionRunningStatus, Never> {
        stateSubject.eraseToAnyPublisher()
    }
    
    /// Local markers (events that don't contribute to matching)
    private var localMarkers: [InteractionLocalEvent] = []
    
    /// Sorted list of local events (sorted by time)
    private var localEvents: [InteractionLocalEvent] = []
    
    /// Timer task for timeout handling
    private var timerTask: Task<Void, Never>?
    
    /// Whether interaction is closed
    private var isInteractionClosed: Bool = true
    
    /// Name of the interaction (from config)
    let name: String
    
    /// Current state (for observation)
    var currentStatus: InteractionRunningStatus {
        return interactionRunningStatus
    }
    
    init(interactionConfig: InteractionConfig) {
        self.interactionConfig = interactionConfig
        self.name = interactionConfig.name
        // Initialize state subject with initial value
        stateSubject.send(interactionRunningStatus)
    }
    
    /// Check and add an event to the tracker
    /// - Parameters:
    ///   - event: The event to process
    func checkAndAdd(event: InteractionLocalEvent) {
        // Check if event matches any event in the config or global blacklisted events
        let matchesConfig = InteractionUtil.matchesAny(event, interactionConfig.events) ||
                           InteractionUtil.matchesAny(event, interactionConfig.globalBlacklistedEvents)
        
        guard matchesConfig else {
            return
        }
        
        // Add event to sorted list
        insertEventSorted(event)
        
        // Generate new interaction ID if needed
        let interactionId: String
        if isInteractionClosed {
            isInteractionClosed = false
            interactionId = UUID().uuidString
        } else {
            if case .ongoingMatch(_, let ongoingId, _, _) = interactionRunningStatus {
                interactionId = ongoingId
            } else {
                interactionId = UUID().uuidString
            }
        }
        
        // Match sequence
        guard let matchResult = InteractionUtil.matchSequence(
            ongoingMatchInteractionId: interactionId,
            localEvents: localEvents,
            localMarkers: localMarkers,
            interactionConfig: interactionConfig
        ) else {
            isInteractionClosed = true
            return
        }
        
        // Process match result
        let newInteractionStatus: InteractionRunningStatus
        if matchResult.shouldResetList {
            if matchResult.shouldTakeFirstEvent,
               let lastEvent = localEvents.last,
               InteractionUtil.matches(lastEvent, interactionConfig.firstEvent) {
                // Keep last event and start new match
                if case .ongoingMatch(let ongoing) = matchResult.interactionStatus {
                    // Create error interaction if needed
                    let errorStatus = createErrorInteraction(
                        interactionId: ongoing.interactionId,
                        interactionConfig: interactionConfig,
                        localEvents: localEvents,
                        localMarkers: localMarkers
                    )
                    interactionRunningStatus = errorStatus
                    
                    // Clear and add last event
                    localEvents.removeAll()
                    localEvents.append(lastEvent)
                    
                    // Start new match
                    newInteractionStatus = .ongoingMatch(
                        index: 0,
                        interactionId: UUID().uuidString,
                        interactionConfig: interactionConfig,
                        interaction: nil
                    )
                } else {
                    newInteractionStatus = matchResult.interactionStatus
                }
            } else {
                // Close interaction and clear events
                isInteractionClosed = true
                localEvents.removeAll()
                newInteractionStatus = matchResult.interactionStatus
            }
        } else {
            newInteractionStatus = matchResult.interactionStatus
        }
        
        
        // Launch/reset timer
        launchResetTimer(newInteractionStatus)
        
        // Update status
        interactionRunningStatus = newInteractionStatus
    }
    
    /// Add a marker event (doesn't contribute to matching)
    func addMarker(_ event: InteractionLocalEvent) {
        localMarkers.append(event)
    }
    
    /// Insert event into sorted list (by timeInNano)
    private func insertEventSorted(_ event: InteractionLocalEvent) {
        let index = localEvents.firstIndex { $0.timeInNano >= event.timeInNano } ?? localEvents.count
        localEvents.insert(event, at: index)
    }
    
    /// Launch or reset timeout timer
    private func launchResetTimer(_ newValue: InteractionRunningStatus) {
        // Cancel existing timer
        timerTask?.cancel()
        timerTask = nil
        
        // Only start timer for ongoing matches without completed interaction
        if case .ongoingMatch(let ongoing) = newValue, ongoing.interaction == nil {
            let timeOfDelay = interactionConfig.thresholdInMs + 10
            let timerInteractionId = ongoing.interactionId
            
            timerTask = Task { [weak self] in
                guard let self = self else { return }
                
                let delayNanoseconds = UInt64(timeOfDelay) * 1_000_000
                
                guard !Task.isCancelled else { return }
                
                do {
                    try await Task.sleep(nanoseconds: delayNanoseconds)
                } catch {
                    return
                }
                
                guard !Task.isCancelled else { return }
                
                if case .ongoingMatch(let current) = self.interactionRunningStatus,
                   current.interaction == nil,
                   current.interactionId == timerInteractionId {
                    let errorStatus = self.createErrorInteraction(
                        interactionId: current.interactionId,
                        interactionConfig: self.interactionConfig,
                        localEvents: self.localEvents,
                        localMarkers: self.localMarkers
                    )
                    
                    self.isInteractionClosed = true
                    self.interactionRunningStatus = errorStatus
                    self.localEvents.removeAll()
                }
            }
        }
    }
    
    /// Create error interaction from ongoing match
    private func createErrorInteraction(
        interactionId: String,
        interactionConfig: InteractionConfig,
        localEvents: [InteractionLocalEvent],
        localMarkers: [InteractionLocalEvent]
    ) -> InteractionRunningStatus {
        let errorInteraction = InteractionUtil.buildPulseInteraction(
            interactionId: interactionId,
            interactionConfig: interactionConfig,
            events: localEvents,
            localMarkers: localMarkers,
            isSuccessInteraction: false
        )
        
        if case .ongoingMatch(let ongoing) = interactionRunningStatus {
            return .ongoingMatch(
                index: ongoing.index,
                interactionId: ongoing.interactionId,
                interactionConfig: ongoing.interactionConfig,
                interaction: errorInteraction
            )
        }
        
        return .noOngoingMatch(oldOngoingInteractionRunningStatus: nil)
    }
}

