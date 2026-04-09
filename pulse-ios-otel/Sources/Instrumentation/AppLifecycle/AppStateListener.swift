/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Protocol for receiving app lifecycle state changes.
public protocol AppStateListener: AnyObject {
    func appCreated()
    func appForegrounded()
    func appBackgrounded()
}

/// Default implementations so listeners only need to implement the callbacks they care about.
public extension AppStateListener {
    func appCreated() {}
    func appForegrounded() {}
    func appBackgrounded() {}
}
