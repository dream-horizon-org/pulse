/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Status of an ongoing interaction match
public indirect enum InteractionRunningStatus: Equatable {
    /// No ongoing match
    case noOngoingMatch(oldOngoingInteractionRunningStatus: InteractionRunningStatus?)
    
    /// Ongoing match with current state
    case ongoingMatch(
        index: Int,
        interactionId: String,
        interactionConfig: InteractionConfig,
        interaction: Interaction?
    )
    
    // Equatable conformance
    public static func == (lhs: InteractionRunningStatus, rhs: InteractionRunningStatus) -> Bool {
        switch (lhs, rhs) {
        case (.noOngoingMatch(let lhsOld), .noOngoingMatch(let rhsOld)):
            // Compare old status if both exist
            if let lhsOld = lhsOld, let rhsOld = rhsOld {
                return lhsOld == rhsOld
            }
            return lhsOld == nil && rhsOld == nil
        case (.ongoingMatch(let lhsIndex, let lhsId, _, let lhsInteraction),
              .ongoingMatch(let rhsIndex, let rhsId, _, let rhsInteraction)):
            return lhsIndex == rhsIndex && lhsId == rhsId && lhsInteraction?.id == rhsInteraction?.id
        default:
            return false
        }
    }
}

