/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

public struct PulseKitConfiguration {
    public var includeScreenAttributes: Bool = true
    public var includeNetworkAttributes: Bool = true
    public var includeGlobalAttributes: Bool = true

    public init() {}
    
    public mutating func disableScreenAttributes() {
        includeScreenAttributes = false
    }
    
    public mutating func disableNetworkAttributes() {
        includeNetworkAttributes = false
    }
    
    public mutating func disableGlobalAttributes() {
        includeGlobalAttributes = false
    }
}

