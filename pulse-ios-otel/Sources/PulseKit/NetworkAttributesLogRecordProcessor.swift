/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi
#if os(iOS) && !targetEnvironment(macCatalyst)
import CoreTelephony
#endif

internal class NetworkAttributesLogRecordProcessor: LogRecordProcessor {
    private let nextProcessor: LogRecordProcessor
    
    #if os(iOS) && !targetEnvironment(macCatalyst)
    private var networkStatus: NetworkStatus?
    #endif

    init(nextProcessor: LogRecordProcessor) {
        self.nextProcessor = nextProcessor
        #if os(iOS) && !targetEnvironment(macCatalyst)
        do {
            self.networkStatus = try NetworkStatus()
        } catch {
            // Network status initialization failed, continue without it
        }
        #endif
    }
    
    func onEmit(logRecord: ReadableLogRecord) {
        var enhancedRecord = logRecord
        
        #if os(iOS) && !targetEnvironment(macCatalyst)
        if let netstat = networkStatus {
            let (connectionType, subtype, carrier) = netstat.status()
            
            enhancedRecord.setAttribute(key: SemanticAttributes.networkConnectionType.rawValue, value: AttributeValue.string(connectionType))
            
            if let subtype = subtype {
                enhancedRecord.setAttribute(key: SemanticAttributes.networkConnectionSubtype.rawValue, value: AttributeValue.string(subtype))
            }
            
            // Only add carrier info if it's valid (filter out iOS 16+ placeholder values)
            // iOS 16+ returns "--" for carrier name/ISO code and "65535" for MCC/MNC when unavailable
            // iOS < 16 returns valid carrier information
            // See: https://developer.apple.com/documentation/coretelephony/ctcarrier
            if let carrier = carrier {
                if let carrierName = carrier.carrierName,
                   !carrierName.isEmpty,
                   carrierName != "--" {
                    enhancedRecord.setAttribute(key: SemanticAttributes.networkCarrierName.rawValue, value: AttributeValue.string(carrierName))
                }
                if let isoCountryCode = carrier.isoCountryCode,
                   !isoCountryCode.isEmpty,
                   isoCountryCode != "--" {
                    enhancedRecord.setAttribute(key: SemanticAttributes.networkCarrierIcc.rawValue, value: AttributeValue.string(isoCountryCode))
                }
                if let mobileCountryCode = carrier.mobileCountryCode,
                   !mobileCountryCode.isEmpty,
                   mobileCountryCode != "65535" {
                    enhancedRecord.setAttribute(key: SemanticAttributes.networkCarrierMcc.rawValue, value: AttributeValue.string(mobileCountryCode))
                }
                if let mobileNetworkCode = carrier.mobileNetworkCode,
                   !mobileNetworkCode.isEmpty,
                   mobileNetworkCode != "65535" {
                    enhancedRecord.setAttribute(key: SemanticAttributes.networkCarrierMnc.rawValue, value: AttributeValue.string(mobileNetworkCode))
                }
            }
        }
        #endif
        
        nextProcessor.onEmit(logRecord: enhancedRecord)
    }
    
    func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.shutdown(explicitTimeout: explicitTimeout)
    }
    
    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.forceFlush(explicitTimeout: explicitTimeout)
    }
}

