/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * OTel utilities used by Pulse sampling (metric name sanitization, etc.).
 */

import Foundation

/// Utilities for OpenTelemetry compatibility within Pulse.
public enum PulseOtelUtils {
    /// Sanitizes a metric name for OTel compatibility.
    /// - Parameters:
    ///   - name: Raw metric name from config.
    ///   - fallbackChar: Character to replace invalid chars (default `_`).
    /// - Returns: Sanitized name: alphanumeric, `_`, `.`, `-`, `/`; must start with letter; max 255 chars.
    public static func sanitizeMetricName(
        name: String,
        fallbackChar: Character = "_"
    ) -> String {
        // Replace every non-supported character with fallbackChar.
        // Supported characters: alphanumeric, _, ., -, /
        let sanitized = name.map { char -> Character in
            if char.isLetter || char.isNumber { return char }
            if char == "_" || char == "." || char == "-" || char == "/" { return char }
            return fallbackChar
        }
        let sanitizedStr = String(sanitized)

        // Must start with a letter; add "m" prefix if not
        let withLetterStart: String
        if let first = sanitizedStr.first, first.isLetter {
            withLetterStart = sanitizedStr
        } else {
            withLetterStart = "m" + sanitizedStr
        }

        // Truncate to 255 chars
        if withLetterStart.count <= 255 {
            return withLetterStart
        }
        return String(withLetterStart.prefix(255))
    }
}
