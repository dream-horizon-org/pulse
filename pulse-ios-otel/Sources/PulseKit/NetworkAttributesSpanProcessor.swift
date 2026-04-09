/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

internal class NetworkAttributesSpanProcessor: SpanProcessor {
    var isStartRequired: Bool = true
    var isEndRequired: Bool = false
    
    #if os(iOS) && !targetEnvironment(macCatalyst)
    private var netstatInjector: NetworkStatusInjector?
    #endif

    init() {
        #if os(iOS) && !targetEnvironment(macCatalyst)
        do {
            let netstat = try NetworkStatus()
            self.netstatInjector = NetworkStatusInjector(netstat: netstat)
        } catch {
            // Network status initialization failed, continue without it
        }
        #endif
    }
    
    func onStart(parentContext: SpanContext?, span: ReadableSpan) {
        #if os(iOS) && !targetEnvironment(macCatalyst)
        if let injector = netstatInjector {
            injector.inject(span: span)
        }
        #endif
    }
    
    func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {
    }
    
    func forceFlush(timeout: TimeInterval?) {
    }
}

