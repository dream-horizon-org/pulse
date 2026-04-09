/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

internal class ScreenAttributesLogRecordProcessor: LogRecordProcessor {
    private let visibleScreenTracker: VisibleScreenTracker
    private let nextProcessor: LogRecordProcessor
    
    init(
        visibleScreenTracker: VisibleScreenTracker = VisibleScreenTracker.shared,
        nextProcessor: LogRecordProcessor
    ) {
        self.visibleScreenTracker = visibleScreenTracker
        self.nextProcessor = nextProcessor
    }
    
    func onEmit(logRecord: ReadableLogRecord) {
        var enhancedRecord = logRecord
        let screenName = visibleScreenTracker.currentlyVisibleScreen
        enhancedRecord.setAttribute(key: PulseAttributes.screenName, value: AttributeValue.string(screenName))
        if let previousScreen = visibleScreenTracker.previouslyVisibleScreen {
            enhancedRecord.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreen))
        }
        nextProcessor.onEmit(logRecord: enhancedRecord)
    }
    
    func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.shutdown(explicitTimeout: explicitTimeout)
    }
    
    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.forceFlush(explicitTimeout: explicitTimeout)
    }
}

