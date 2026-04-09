/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SessionReplayRemoteConfig: Parser for Session Replay config from backend feature config.
 * Maps backend JSON fields to iOS SessionReplayConfig structure.
 */

import Foundation

/// Remote config structure for Session Replay, parsed from backend feature config JSON.
internal struct SessionReplayRemoteConfig: Codable {
    let textAndInputPrivacy: String?
    let imagePrivacy: String?
    let throttleDelayMs: Int?
    let screenshotScale: Float?
    let screenshotQuality: Int?
    let flushIntervalSeconds: Int?
    let flushAt: Int?
    let maxBatchSize: Int?
    let replayApiBaseUrl: String?
    
    enum CodingKeys: String, CodingKey {
        case textAndInputPrivacy
        case imagePrivacy
        case throttleDelayMs
        case screenshotScale
        case screenshotQuality
        case flushIntervalSeconds
        case flushAt
        case maxBatchSize
        case replayApiBaseUrl
    }
    
    /// Creates SessionReplayRemoteConfig from PulseFeatureConfig's config dictionary.
    /// Returns nil if config is absent or parsing fails.
    static func from(featureConfig: PulseFeatureConfig) -> SessionReplayRemoteConfig? {
        guard let configDict = featureConfig.config else {
            return nil
        }
        
        // Convert [String: AnyCodable] to [String: Any] for JSON encoding
        // Handle NSNull values properly
        let anyDict = configDict.mapValues { codable -> Any in
            let value = codable.value
            if value is NSNull {
                return NSNull()
            }
            return value
        }
        
        // Encode to JSON data, then decode as SessionReplayRemoteConfig
        guard JSONSerialization.isValidJSONObject(anyDict),
              let jsonData = try? JSONSerialization.data(withJSONObject: anyDict),
              let decoded = try? JSONDecoder().decode(SessionReplayRemoteConfig.self, from: jsonData) else {
            return nil
        }
        
        return decoded
    }
}
