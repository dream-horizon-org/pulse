/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SessionReplayConfigMerger: Merges backend remote config with local config.
 * Strategy: backend overrides local, local provides defaults.
 */

import Foundation

extension SessionReplayConfig {
    /// Merges remote config (from backend) with local config (from app code).
    /// Backend values override local values when present; local provides defaults.
    /// 
    /// - Parameters:
    ///   - remote: Remote config from backend feature config (optional)
    ///   - local: Local config from app initialization (provides defaults)
    /// - Returns: Merged SessionReplayConfig
    static func merge(
        remote: SessionReplayRemoteConfig?,
        local: SessionReplayConfig
    ) -> SessionReplayConfig {
        guard let remote = remote else {
            return local
        }
        
        // Parse enum values from strings
        let textPrivacy: TextAndInputPrivacy? = remote.textAndInputPrivacy.flatMap { value in
            switch value.uppercased() {
            case "MASK_ALL":
                return .maskAll
            case "MASK_ALL_INPUTS":
                return .maskAllInputs
            case "MASK_SENSITIVE_INPUTS":
                return .maskSensitiveInputs
            default:
                return nil
            }
        }
        
        let imagePrivacy: ImagePrivacy? = remote.imagePrivacy.flatMap { value in
            switch value.uppercased() {
            case "MASK_ALL":
                return .maskAll
            case "MASK_NONE":
                return .maskNone
            default:
                return nil
            }
        }
        
        // Convert screenshotQuality from Int (0-100) to CGFloat (0.0-1.0)
        let compressionQuality: CGFloat? = remote.screenshotQuality.map { quality in
            // Clamp to valid range and convert to 0.0-1.0
            let clamped = max(0, min(100, quality))
            return CGFloat(clamped) / 100.0
        }
        
        // Convert screenshotScale from Float to CGFloat and clamp to valid range
        let screenshotScale: CGFloat? = remote.screenshotScale.map { scale in
            let clamped = max(0.01, min(1.0, CGFloat(scale)))
            return clamped
        }
        
        // Convert flushIntervalSeconds from Int to TimeInterval
        let flushIntervalSeconds: TimeInterval? = remote.flushIntervalSeconds.map { TimeInterval($0) }
        
        // Build merged config: backend overrides local, local provides defaults
        return SessionReplayConfig(
            captureIntervalMs: remote.throttleDelayMs ?? local.captureIntervalMs,
            compressionQuality: compressionQuality ?? local.compressionQuality,
            textAndInputPrivacy: textPrivacy ?? local.textAndInputPrivacy,
            imagePrivacy: imagePrivacy ?? local.imagePrivacy,
            screenshotScale: screenshotScale ?? local.screenshotScale,
            flushIntervalSeconds: flushIntervalSeconds ?? local.flushIntervalSeconds,
            flushAt: remote.flushAt ?? local.flushAt,
            maxBatchSize: remote.maxBatchSize ?? local.maxBatchSize,
            replayEndpointBaseUrl: remote.replayApiBaseUrl ?? local.replayEndpointBaseUrl,
            // Local-only settings are preserved (not in backend config)
            maskViewClasses: local.maskViewClasses,
            unmaskViewClasses: local.unmaskViewClasses
        )
    }
}
