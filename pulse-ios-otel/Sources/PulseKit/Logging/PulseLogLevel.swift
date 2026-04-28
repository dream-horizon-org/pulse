/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

public enum PulseLogLevel: Int, Comparable {
    case verbose = 0
    case debug = 1
    case info = 2
    case warn = 3
    case error = 4
    case none = 5

    public static func < (lhs: PulseLogLevel, rhs: PulseLogLevel) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}
