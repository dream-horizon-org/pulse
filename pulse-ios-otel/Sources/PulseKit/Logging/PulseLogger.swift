/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Central logging for the Pulse SDK. All modules should use PulseLogger
 * so logs have a consistent tag and obey the runtime log level.
 */

import Foundation

package enum PulseLogger {
    private static let tag = "PulseSDK"

    package static var currentLevel: PulseLogLevel = .none

    private static func write(_ body: () -> String) {
        print("\(tag) \(body())")
    }

    package static func verbose(_ message: @autoclosure () -> String) {
        guard currentLevel <= .verbose else { return }
        write { message() }
    }

    package static func debug(_ message: @autoclosure () -> String) {
        guard currentLevel <= .debug else { return }
        write { message() }
    }

    package static func info(_ message: @autoclosure () -> String) {
        guard currentLevel <= .info else { return }
        write { message() }
    }

    package static func warn(_ message: @autoclosure () -> String) {
        guard currentLevel <= .warn else { return }
        write { message() }
    }

    package static func error(_ message: @autoclosure () -> String) {
        guard currentLevel <= .error else { return }
        write { message() }
    }

    /// Backward-compatible convenience that logs at INFO level.
    package static func log(_ message: String) {
        info(message)
    }
}
