/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk

internal struct InstallationContext {
    let tracerProvider: TracerProvider

    let loggerProvider: LoggerProvider

    let openTelemetry: OpenTelemetry

    let endpointBaseUrl: String

    let endpointHeaders: [String: String]

    let flushLogProcessor: (() -> Void)?

    /// Project identifier (from PulseKit.initialize).
    let projectId: String

    /// Returns the current user ID at call time (or nil). Thread-safe.
    let userIdProvider: () -> String?
}
