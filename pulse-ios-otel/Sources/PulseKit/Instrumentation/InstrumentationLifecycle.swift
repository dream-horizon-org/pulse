/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

internal protocol InstrumentationLifecycle {
    func initialize(ctx: InstallationContext)
    func uninstall()
}
