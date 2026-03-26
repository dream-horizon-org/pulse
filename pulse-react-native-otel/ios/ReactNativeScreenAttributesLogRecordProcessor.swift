import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi
import PulseKit

/**
 * Log record processor that overrides screen.name with React Native screen name.
 */
internal class ReactNativeScreenAttributesLogRecordProcessor: LogRecordProcessor {
    private let nextProcessor: LogRecordProcessor
    
    init(nextProcessor: LogRecordProcessor) {
        self.nextProcessor = nextProcessor
    }
    
    func onEmit(logRecord: ReadableLogRecord) {
        var enhancedRecord = logRecord
        if let screenName = ReactNativeScreenNameTracker.getCurrentScreenName() {
            enhancedRecord.setAttribute(key: PulseAttributes.screenName, value: AttributeValue.string(screenName))
        }
        if let previousScreenName = ReactNativeScreenNameTracker.getPreviousScreenName() {
            enhancedRecord.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreenName))
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

