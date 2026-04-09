/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Thread-safe persistence for PulseSdkConfig. SharedPreferences name "pulse_sdk_config", key "sdk_config".
 */

import Foundation

/// Storage for persisted SDK config. Uses a single UserDefaults instance (either a named suite or standard).
/// All access is serialized on a dedicated queue for thread safety.
public final class PulseSdkConfigStorage {
    /// UserDefaults suite name for production.
    public static let suiteName = "pulse_sdk_config"
    /// Key for the JSON string.
    public static let configKey = "sdk_config"

    private let queue = DispatchQueue(label: "com.pulse.ios.sdk.sampling.config.storage", qos: .utility)
    private let defaults: UserDefaults?

    private static let jsonEncoder: JSONEncoder = { JSONEncoder() }()
    private static let jsonDecoder: JSONDecoder = { JSONDecoder() }()

    /// - Parameter suiteName: Which store to use. Pass `PulseSdkConfigStorage.suiteName` (default) for production—
    ///   uses a dedicated UserDefaults suite so config is isolated. Pass `nil` only for tests, to use `UserDefaults.standard`
    ///   so tests don't need a real suite. We use one or the other, not both.
    public init(suiteName: String? = PulseSdkConfigStorage.suiteName) {
        if let name = suiteName {
            self.defaults = UserDefaults(suiteName: name)
        } else {
            self.defaults = UserDefaults.standard
        }
    }

    /// Reads and decodes the persisted config. Returns nil if none stored or decode fails.
    /// On decode failure we do not crash—we return nil and log; caller should use Pulse.initialize defaults.
    /// Safe to call from any thread.
    public func load() -> PulseSdkConfig? {
        queue.sync {
            guard let data = defaults?.string(forKey: PulseSdkConfigStorage.configKey),
                  let jsonData = data.data(using: .utf8) else {
                return nil
            }
            do {
                return try PulseSdkConfigStorage.jsonDecoder.decode(PulseSdkConfig.self, from: jsonData)
            } catch {
                PulseLogger.log("Config: load from persistence decode failed (using defaults).")
                return nil
            }
        }
    }

    /// Encodes and writes the config asynchronously. Overwrites any existing value.
    /// Use when you don't need to wait for the write to finish (fire-and-forget).
    /// Safe to call from any thread.
    public func save(_ config: PulseSdkConfig) {
        queue.async { [weak self] in
            guard let self = self,
                  let data = try? PulseSdkConfigStorage.jsonEncoder.encode(config),
                  let string = String(data: data, encoding: .utf8) else {
                return
            }
            self.defaults?.set(string, forKey: PulseSdkConfigStorage.configKey)
            self.defaults?.synchronize()
        }
    }

    /// Encodes and writes the config synchronously. Use when you must ensure the write completes before continuing
    /// (e.g. inside a background task that will end—so the process doesn't exit with the write still pending).
    /// Safe to call from any thread.
    public func saveSync(_ config: PulseSdkConfig) {
        queue.sync {
            guard let data = try? PulseSdkConfigStorage.jsonEncoder.encode(config),
                  let string = String(data: data, encoding: .utf8) else {
                return
            }
            defaults?.set(string, forKey: PulseSdkConfigStorage.configKey)
            defaults?.synchronize()
        }
    }
}
