/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Core utility for matching event sequences and building interactions
internal enum InteractionUtil {
    internal struct InteractionBuildError {
        let type: InteractionErrorType
        var timeoutExpectedEventName: String?
        var sequenceViolationExpectedEventName: String?
        var sequenceViolationReceivedEventName: String?
    }

    private static func interactionErrorMessage(_ error: InteractionBuildError) -> String {
        switch error.type {
        case .timeout:
            if let name = error.timeoutExpectedEventName {
                return "Timed out while waiting for event \"\(name)\"."
            }
            return "Timed out before the next expected event arrived."
        case .sequenceViolation:
            if let expected = error.sequenceViolationExpectedEventName,
               let received = error.sequenceViolationReceivedEventName {
                return "Expected event \"\(expected)\", received \"\(received)\"."
            }
            return "An event did not match the next expected event in this interaction."
        }
    }

    /// Result of matching a sequence
    struct MatchResult {
        let shouldTakeFirstEvent: Bool
        let shouldResetList: Bool
        let interactionStatus: InteractionRunningStatus
    }

    /**
     * Matches local events against interaction configuration sequence
     * Returns nil when there is event of interest but because of ordering it didn't create any
     * change in the matching
     * For example global black listed event came but before or after the first or last event
     * respectively
     */
    static func matchSequence(
        ongoingMatchInteractionId: String,
        localEvents: [InteractionLocalEvent],
        localMarkers: [InteractionLocalEvent],
        interactionConfig: InteractionConfig
    ) -> MatchResult? {
        var stepWiseTimeInNano: [InteractionLocalEvent] = []
        var configEventIndex = 0
        var isMatchOnGoing = false

        func resetMatching() {
            stepWiseTimeInNano.removeAll()
            configEventIndex = 0
            isMatchOnGoing = false
        }

        var newInteractionStatus: MatchResult?

        var localEventIndex = 0
        while localEventIndex < localEvents.count {
            let localEvent = localEvents[localEventIndex]

            // Check for global blacklisted events during ongoing match
            if isMatchOnGoing && matchesAny(localEvent, interactionConfig.globalBlacklistedEvents) {
                return MatchResult(
                    shouldTakeFirstEvent: false,
                    shouldResetList: true,
                    interactionStatus: .noOngoingMatch(oldOngoingInteractionRunningStatus: nil)
                )
            }

            let configEvent = interactionConfig.events[configEventIndex]
            let isMatch = matches(localEvent, configEvent)

            if isMatch {
                if configEvent.isBlacklisted {
                    newInteractionStatus = MatchResult(
                        shouldTakeFirstEvent: false,
                        shouldResetList: true,
                        interactionStatus: .noOngoingMatch(oldOngoingInteractionRunningStatus: nil)
                    )
                } else {
                    stepWiseTimeInNano.append(localEvent)
                    configEventIndex += 1

                    if configEventIndex == interactionConfig.eventsSize {
                        isMatchOnGoing = false
                        newInteractionStatus = MatchResult(
                            shouldTakeFirstEvent: false,
                            shouldResetList: true,
                            interactionStatus: .ongoingMatch(
                                index: configEventIndex - 1,
                                interactionId: ongoingMatchInteractionId,
                                interactionConfig: interactionConfig,
                                interaction: buildPulseInteraction(
                                    interactionId: ongoingMatchInteractionId,
                                    interactionConfig: interactionConfig,
                                    events: stepWiseTimeInNano,
                                    localMarkers: localMarkers,
                                    error: nil
                                )
                            )
                        )
                    } else {
                        isMatchOnGoing = true
                        // ongoing match
                        newInteractionStatus = MatchResult(
                            shouldTakeFirstEvent: false,
                            shouldResetList: false,
                            interactionStatus: .ongoingMatch(
                                index: configEventIndex - 1,
                                interactionId: ongoingMatchInteractionId,
                                interactionConfig: interactionConfig,
                                interaction: nil
                            )
                        )
                    }
                }
            } else if configEvent.isBlacklisted {
                // Skip the blacklisted config event, but keep checking the current local event
                // against the next config event
                configEventIndex += 1
                continue
            } else if isMatchOnGoing {
                isMatchOnGoing = false
                newInteractionStatus = MatchResult(
                    shouldTakeFirstEvent: true,
                    shouldResetList: true,
                    interactionStatus: .ongoingMatch(
                        index: configEventIndex - 1,
                        interactionId: ongoingMatchInteractionId,
                        interactionConfig: interactionConfig,
                        interaction: buildPulseInteraction(
                            interactionId: ongoingMatchInteractionId,
                            interactionConfig: interactionConfig,
                            events: stepWiseTimeInNano,
                            localMarkers: localMarkers,
                            error: InteractionBuildError(
                                type: .sequenceViolation,
                                sequenceViolationExpectedEventName: configEvent.name,
                                sequenceViolationReceivedEventName: localEvent.name
                            )
                        )
                    )
                )
            } else {
                // no match is ongoing
                // newInteractionStatus remains nil
            }

            localEventIndex += 1
        }

        if newInteractionStatus?.shouldResetList == true {
            resetMatching()
        }

        return newInteractionStatus
    }

    /// Check if local event matches interaction event
    static func matches(_ localEvent: InteractionLocalEvent, _ interactionEvent: InteractionEvent) -> Bool {
        if localEvent.name != interactionEvent.name {
            return false
        }

        guard let propsInteractionConfig = interactionEvent.props else {
            return true
        }

        guard let propsLocalEvent = localEvent.props else {
            return false
        }

        return propsInteractionConfig.allSatisfy { prop in
            matchesProperty(prop, in: propsLocalEvent)
        }
    }

    /// Check if local event matches any of the interaction events
    static func matchesAny(_ localEvent: InteractionLocalEvent, _ interactionEvents: [InteractionEvent]) -> Bool {
        return interactionEvents.contains { matches(localEvent, $0) }
    }

    /// Check if property matches in the local event props
    private static func matchesProperty(_ propInteractionConfig: InteractionAttrsEntry, in propsLocalEvent: [String: String]) -> Bool {
        let propName = propInteractionConfig.name
        let propValue = propInteractionConfig.value
        let operatorValue = propInteractionConfig.operator

        guard let actualValue = propsLocalEvent[propName] else {
            return false
        }

        return matchPropValue(expectedValue: propValue, operator: operatorValue, actualValue: actualValue)
    }

    /// Match property value using operator
    private static func matchPropValue(
        expectedValue: String,
        operator: String,
        actualValue: String
    ) -> Bool {
        let actualValueLower = actualValue.lowercased()
        let expectedValueLower = expectedValue.lowercased()
        let operatorUpper = `operator`.uppercased()

        guard let op = InteractionAttributes.Operators(rawValue: operatorUpper) else {
            return false
        }

        switch op {
        case .equals:
            return actualValue == expectedValue
        case .notEquals:
            return actualValue != expectedValue
        case .contains:
            return actualValueLower.contains(expectedValueLower)
        case .notContains:
            return !actualValueLower.contains(expectedValueLower)
        case .startsWith:
            return actualValueLower.hasPrefix(expectedValueLower)
        case .endsWith:
            return actualValueLower.hasSuffix(expectedValueLower)
        }
    }

    /// Build the final Interaction object
    static func buildPulseInteraction(
        interactionId: String,
        interactionConfig: InteractionConfig,
        events: [InteractionLocalEvent],
        localMarkers: [InteractionLocalEvent],
        error: InteractionBuildError? = nil
    ) -> Interaction {
        guard let firstEvent = events.first, let lastEvent = events.last else {
            preconditionFailure("buildPulseInteraction requires at least one event")
        }

        let interactionName = interactionConfig.name
        let interactionConfigId = interactionConfig.id
        let lastEventTimeInNano = lastEvent.timeInNano

        let (timeDifferenceInNano, timeCategory, upTimeIndex): (Int64?, InteractionAttributes.TimeCategory?, Double?)

        if error == nil {
            let timeDiffInNano = lastEvent.timeInNano - firstEvent.timeInNano
            let timeDifferenceInMs = timeDiffInNano / 1_000_000
            let lowerLimitInMs = interactionConfig.uptimeLowerLimitInMs
            let midLimitInMs = interactionConfig.uptimeMidLimitInMs
            let upperLimitInMs = interactionConfig.uptimeUpperLimitInMs

            let (upTimeIdx, timeCat): (Double, InteractionAttributes.TimeCategory)
            if timeDifferenceInMs <= lowerLimitInMs {
                upTimeIdx = 1.0
                timeCat = .excellent
            } else if timeDifferenceInMs <= midLimitInMs {
                upTimeIdx = getUpTimeIndex(
                    timeDifferenceInMs: timeDifferenceInMs,
                    lowerLimit: lowerLimitInMs,
                    upperLimit: upperLimitInMs
                )
                timeCat = .good
            } else if timeDifferenceInMs <= upperLimitInMs {
                upTimeIdx = getUpTimeIndex(
                    timeDifferenceInMs: timeDifferenceInMs,
                    lowerLimit: lowerLimitInMs,
                    upperLimit: upperLimitInMs
                )
                timeCat = .average
            } else {
                upTimeIdx = 0.0
                timeCat = .poor
            }

            (timeDifferenceInNano, timeCategory, upTimeIndex) = (timeDiffInNano, timeCat, upTimeIdx)
        } else {
            (timeDifferenceInNano, timeCategory, upTimeIndex) = (nil, nil, nil)
        }

        let maps: [String: Any?] = {
            var m: [String: Any?] = [
                InteractionAttributes.name: interactionName,
                InteractionAttributes.configId: interactionConfigId,
                InteractionAttributes.lastEventTimeInNano: lastEventTimeInNano,
                InteractionAttributes.localEvents: events,
                InteractionAttributes.markerEvents: localMarkers,
                InteractionAttributes.apdexScore: upTimeIndex,
                InteractionAttributes.userCategory: timeCategory?.rawValue,
                InteractionAttributes.timeToCompleteInNano: timeDifferenceInNano,
                InteractionAttributes.isError: error != nil
            ]
            if let error = error {
                m[InteractionAttributes.errorType] = error.type.code
                m[InteractionAttributes.errorMessage] = interactionErrorMessage(error)
            }
            return m
        }()

        return Interaction(
            id: interactionId,
            name: interactionName,
            props: maps
        )
    }

    /// Calculate uptime index for APDEX score
    private static func getUpTimeIndex(
        timeDifferenceInMs: Int64,
        lowerLimit: Int64,
        upperLimit: Int64
    ) -> Double {
        guard upperLimit > lowerLimit else {
            return 0.0
        }
        return 1.0 - (1.0 * Double(timeDifferenceInMs - lowerLimit) / Double(upperLimit - lowerLimit))
    }
}
