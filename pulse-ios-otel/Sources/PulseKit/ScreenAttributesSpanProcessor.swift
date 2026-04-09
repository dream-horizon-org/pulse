/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

internal class ScreenAttributesSpanProcessor: SpanProcessor {
    var isStartRequired: Bool = true
    var isEndRequired: Bool = false
    
    private let visibleScreenTracker: VisibleScreenTracker
    
    init(visibleScreenTracker: VisibleScreenTracker = VisibleScreenTracker.shared) {
        self.visibleScreenTracker = visibleScreenTracker
    }
    
    func onStart(parentContext: SpanContext?, span: ReadableSpan) {
        if span.toSpanData().attributes[PulseAttributes.screenName] == nil {
            let screenName = visibleScreenTracker.currentlyVisibleScreen
            span.setAttribute(key: PulseAttributes.screenName, value: AttributeValue.string(screenName))
        }
        if span.toSpanData().attributes[PulseAttributes.lastScreenName] == nil,
           let previousScreen = visibleScreenTracker.previouslyVisibleScreen {
            span.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreen))
        }
    }
    
    func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {
    }
    
    func forceFlush(timeout: TimeInterval?) {
    }
}

