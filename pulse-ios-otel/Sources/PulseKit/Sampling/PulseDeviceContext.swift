/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Device context for session sampling rules.
 * Provides current device/app attribute values for PulseDeviceAttributeName matching.
 */

import Foundation

#if canImport(UIKit)
import UIKit
#endif

/// Provides current device and app attribute values for session sampling rule matching.
public struct PulseDeviceContext {
    /// Shared instance using current device/app state.
    public static var current: PulseDeviceContext {
        PulseDeviceContext()
    }

    /// Returns the current value for the given device attribute, or nil if unavailable.
    public func value(for attribute: PulseDeviceAttributeName) -> String? {
        let val: String?
        switch attribute {
        case .os_version:
            val = Self.osVersion
        case .app_version:
            val = Self.appVersion
        case .country:
            val = Self.country
        case .state:
            val = nil // TODO: state handling
        case .platform:
            val = PulseSdkName.pulse_ios_swift.rawValue
        case .unknown:
            val = nil
        }
        return val
    }

    // MARK: - Private attribute resolvers

    private static var osVersion: String {
        #if os(iOS) || os(tvOS)
        return UIDevice.current.systemVersion
        #elseif os(macOS) || os(watchOS)
        return ProcessInfo.processInfo.operatingSystemVersionString
        #else
        return "unknown"
        #endif
    }

    private static var appVersion: String? {
        guard let info = Bundle.main.infoDictionary else { return nil }
        return info["CFBundleShortVersionString"] as? String
            ?? info["CFBundleVersion"] as? String
    }

    private static var country: String? {
        if #available(macOS 13, iOS 16, tvOS 16, watchOS 9, *) {
            return Locale.current.region?.identifier
        }
        return (Locale.current as NSLocale).object(forKey: .countryCode) as? String
    }
}

// MARK: - PulseDeviceAttributeName matching

extension PulseDeviceAttributeName {
    /// Returns true if the current device value for this attribute matches the given regex pattern.
    public func matches(deviceContext: PulseDeviceContext, value regexPattern: String) -> Bool {
        guard let currentValue = deviceContext.value(for: self) else {
            return false
        }
        // Defensive: strip surrounding double-quotes if config/UI accidentally wrapped the value (e.g. "\"1.0\"")
        let trimmedPattern = regexPattern.hasPrefix("\"") && regexPattern.hasSuffix("\"") && regexPattern.count >= 2
            ? String(regexPattern.dropFirst().dropLast())
            : regexPattern
        return Self.matchesRegex(pattern: trimmedPattern, string: currentValue)
    }

    /// Matches regex against the full string (same semantics as Kotlin String.matches(Regex)).
    private static func matchesRegex(pattern: String, string: String) -> Bool {
        do {
            let regex = try NSRegularExpression(pattern: pattern)
            let nsString = string as NSString
            let fullRange = NSRange(location: 0, length: nsString.length)
            let match = regex.firstMatch(in: string, options: [], range: fullRange)
            guard let m = match else { return false }
            return m.range == fullRange
        } catch {
            return false
        }
        }
}

// MARK: - PulseSessionSamplingRule matching

extension PulseSessionSamplingRule {
    /// Returns true if this rule matches the current device context.
    /// Calls name.matches(context, value).
    public func matches(deviceContext: PulseDeviceContext) -> Bool {
        return name.matches(deviceContext: deviceContext, value: value)
    }
}
