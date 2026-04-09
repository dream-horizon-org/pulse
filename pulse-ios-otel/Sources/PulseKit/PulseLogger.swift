/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Central logging for the Pulse SDK. All modules should use this so logs have a single tag [Pulse]
 * and developers can predict flow and diagnose issues from the terminal.
 */

import Foundation

enum PulseLogger {
    private static let tag = "[Pulse]"

    /// Log a message to the console with the Pulse tag. Use for init flow, config, errors.
    static func log(_ message: String) {
        print("\(tag) \(message)")
    }
}
