/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Central logging for the Pulse SDK. All modules should use PulseLogger
 * so logs have a consistent tag and obey the runtime log level.
 */

import Foundation
#if canImport(os.log)
import os.log
#endif

package enum PulseLogger {
    private static let subsystem = "com.pulse.sdk"
    #if canImport(os.log)
    private static let log = OSLog(subsystem: subsystem, category: "PulseSDK")
    #endif

    package static var currentLevel: PulseLogLevel = .none

    package static func verbose(_ message: @autoclosure () -> String) {
        guard currentLevel <= .verbose else { return }
        #if canImport(os.log)
        os_log("%{public}@", log: log, type: .debug, message() as CVarArg)
        #else
        print("[PulseSDK] \(message())")
        #endif
    }

    package static func debug(_ message: @autoclosure () -> String) {
        guard currentLevel <= .debug else { return }
        #if canImport(os.log)
        os_log("%{public}@", log: log, type: .debug, message() as CVarArg)
        #else
        print("[PulseSDK] \(message())")
        #endif
    }

    package static func info(_ message: @autoclosure () -> String) {
        guard currentLevel <= .info else { return }
        #if canImport(os.log)
        os_log("%{public}@", log: log, type: .info, message() as CVarArg)
        #else
        print("[PulseSDK] \(message())")
        #endif
    }

    package static func warn(_ message: @autoclosure () -> String) {
        guard currentLevel <= .warn else { return }
        #if canImport(os.log)
        os_log("%{public}@", log: log, type: .default, message() as CVarArg)
        #else
        print("[PulseSDK] \(message())")
        #endif
    }

    package static func error(_ message: @autoclosure () -> String) {
        guard currentLevel <= .error else { return }
        #if canImport(os.log)
        os_log("%{public}@", log: log, type: .error, message() as CVarArg)
        #else
        print("[PulseSDK] \(message())")
        #endif
    }

    /// Backward-compatible convenience that logs at INFO level.
    package static func log(_ message: String) {
        info(message)
    }
}
