/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Session sampling: parser and decision (Batch 2, LLD §6).
 * Evaluates rules with device context, returns session sample rate, and decides shouldSampleThisSession.
 */

import Foundation
import Security

// MARK: - PulseSessionParser protocol

/// Returns the session sample rate for the current session based on device context and config.
public protocol PulseSessionParser {
    /// Returns a Float in [0, 1] for the current session.
    /// - Parameters:
    ///   - deviceContext: Current device/app attribute values
    ///   - samplingConfig: Sampling rules and default from config
    ///   - currentSdkName: e.g. .pulse_ios_swift
    func parses(
        deviceContext: PulseDeviceContext,
        samplingConfig: PulseSamplingConfig,
        currentSdkName: PulseSdkName
    ) -> Float
}

// MARK: - Default implementation (PulseSessionConfigParser)

/// Default session parser: evaluate rules in order, first match wins; else default.
public struct PulseSessionConfigParser: PulseSessionParser {
    public init() {}

    public func parses(
        deviceContext: PulseDeviceContext,
        samplingConfig: PulseSamplingConfig,
        currentSdkName: PulseSdkName
    ) -> Float {
        for rule in samplingConfig.rules {
            guard rule.sdks.contains(currentSdkName) else { continue }
            if rule.matches(deviceContext: deviceContext) {
                return rule.sessionSampleRate
            }
        }
        return samplingConfig.default.sessionSampleRate
    }
}

// MARK: - Test parsers (alwaysOn / alwaysOff, used by Batch 4)

/// Parser that always returns 1 (session always sampled). For testing.
public struct AlwaysOnSessionParser: PulseSessionParser {
    public init() {}

    public func parses(
        deviceContext: PulseDeviceContext,
        samplingConfig: PulseSamplingConfig,
        currentSdkName: PulseSdkName
    ) -> Float {
        1.0
    }
}

/// Parser that always returns 0 (session never sampled). For testing.
public struct AlwaysOffSessionParser: PulseSessionParser {
    public init() {}

    public func parses(
        deviceContext: PulseDeviceContext,
        samplingConfig: PulseSamplingConfig,
        currentSdkName: PulseSdkName
    ) -> Float {
        0.0
    }
}

// MARK: - Session sampling decision

/// Holds the session sampling decision: computed once per session (random draw + parser).
/// The same random value is reused for both session sampling and per-signal signalsToSample
/// to ensure consistent sampling within a session.
public final class PulseSessionSamplingDecision {
    private let _shouldSample: Bool
    private let lock = NSLock()

    /// Random value in [0, 1] drawn once per session. Reused for session sampling and
    /// per-signal signalsToSample probabilistic comparisons.
    public let sessionRandomValue: Float

    public var shouldSampleThisSession: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _shouldSample
    }

    /// Computes and caches the session sampling decision.
    /// Call once per session (e.g. at SDK init when config is available).
    /// - Parameters:
    ///   - deviceContext: Current device context
    ///   - samplingConfig: From sdkConfig.sampling
    ///   - currentSdkName: e.g. .pulse_ios_swift
    ///   - parser: Session parser (default: PulseSessionConfigParser)
    ///   - randomGenerator: Optional; uses secure random by default
    public init(
        deviceContext: PulseDeviceContext = .current,
        samplingConfig: PulseSamplingConfig,
        currentSdkName: PulseSdkName,
        parser: PulseSessionParser = PulseSessionConfigParser(),
        randomGenerator: (() -> Float)? = nil
    ) {
        let samplingRate = parser.parses(
            deviceContext: deviceContext,
            samplingConfig: samplingConfig,
            currentSdkName: currentSdkName
        )
        let randomValue = randomGenerator?() ?? Self.secureRandomFloatInZeroToOne()
        sessionRandomValue = randomValue
        _shouldSample = randomValue <= samplingRate
    }

    /// Secure random float in [0, 1].
    private static func secureRandomFloatInZeroToOne() -> Float {
        var bytes: [UInt8] = Array(repeating: 0, count: 4)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            return Float.random(in: 0 ... 1)
        }
        let value = bytes.withUnsafeBytes { $0.load(as: UInt32.self) }
        return Float(value) / Float(UInt32.max)
    }
}
