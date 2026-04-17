/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Central logging for the Pulse SDK. All modules should use PulseLogger
 * so logs have a consistent tag and obey the runtime log level.
 */

import Foundation
import os

public enum PulseLogger {
    private static let subsystem = "com.pulse.sdk"
    private static let osLogger = os.Logger(subsystem: subsystem, category: "PulseSDK")

    public static var currentLevel: PulseLogLevel = .none

    public static func verbose(_ message: @autoclosure () -> String) {
        guard currentLevel <= .verbose else { return }
        osLogger.debug("\(message(), privacy: .public)")
    }

    public static func debug(_ message: @autoclosure () -> String) {
        guard currentLevel <= .debug else { return }
        osLogger.debug("\(message(), privacy: .public)")
    }

    public static func info(_ message: @autoclosure () -> String) {
        guard currentLevel <= .info else { return }
        osLogger.info("\(message(), privacy: .public)")
    }

    public static func warn(_ message: @autoclosure () -> String) {
        guard currentLevel <= .warn else { return }
        osLogger.warning("\(message(), privacy: .public)")
    }

    public static func error(_ message: @autoclosure () -> String) {
        guard currentLevel <= .error else { return }
        osLogger.error("\(message(), privacy: .public)")
    }

    /// Backward-compatible convenience that logs at INFO level.
    static func log(_ message: String) {
        info(message)
    }
}
