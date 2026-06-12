/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Wire values for `pulse.interaction.error.type` .
internal enum InteractionErrorType: String, CaseIterable {
    case timeout = "timeout"
    case sequenceViolation = "sequence_violation"

    var code: String { rawValue }

    /// — one static map, O(1) lookup by wire string.
    private static let byCode: [String: InteractionErrorType] = Dictionary(
        uniqueKeysWithValues: Self.allCases.map { ($0.code, $0) }
    )

    static func fromCode(_ value: String?) -> InteractionErrorType? {
        guard let value else { return nil }
        return Self.byCode[value]
    }
}
