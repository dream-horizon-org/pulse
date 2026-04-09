/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ClickFeatureRemoteConfig: Parser for click feature config from backend.
 * Maps backend JSON fields to iOS rage configuration structure.
 */

import Foundation

/// Remote config structure for click instrumentation, parsed from backend feature config JSON.
internal struct ClickFeatureRemoteConfig: Decodable {
    let rage: RageConfig?
    
    struct RageConfig: Decodable {
        let timeWindowMs: Int?
        let threshold: Int?
        let radius: Float?
    }
    
    /// Creates ClickFeatureRemoteConfig from PulseFeatureConfig's config dictionary.
    /// Returns nil if config is absent or parsing fails.
    /// 
    /// NOTE: PulseFeatureConfig.config is [String: AnyCodable]? (see PulseSdkConfigModels.swift:287).
    /// This parser converts AnyCodable → JSONSerializable → JSON data → typed struct.
    /// If parsing silently fails (returns nil), rage config defaults are used (safe fallback).
    static func from(featureConfig: PulseFeatureConfig) -> ClickFeatureRemoteConfig? {
        guard let configDict = featureConfig.config else {
            return nil
        }
        
        // Convert [String: AnyCodable] to [String: Any] for JSON encoding
        // Handle NSNull values properly (match SessionReplayRemoteConfig pattern)
        let anyDict = configDict.mapValues { codable -> Any in
            let value = codable.value
            if value is NSNull {
                return NSNull()
            }
            return value
        }
        
        // Encode to JSON data, then decode as ClickFeatureRemoteConfig
        guard JSONSerialization.isValidJSONObject(anyDict),
              let jsonData = try? JSONSerialization.data(withJSONObject: anyDict),
              let decoded = try? JSONDecoder().decode(ClickFeatureRemoteConfig.self, from: jsonData) else {
            // Silent nil return: log warning to surface misconfiguration
            PulseLogger.log("Failed to parse click feature config from backend; using SDK defaults")
            return nil
        }
        
        return decoded
    }
}
