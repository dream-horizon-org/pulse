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
}
