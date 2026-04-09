/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Coordinates config load from persistence and background fetch/persist.
 * Keeps all sampling config logic out of PulseKit; PulseKit only creates the coordinator and calls it.
 */

import Foundation

/// Coordinates loading current SDK config from storage and starting a background config fetch.
/// Receives the final config URL from PulseKit (PulseKit applies default when app passes nil).
public final class PulseSdkConfigCoordinator {
    private let storage: PulseSdkConfigStorage
    /// When true, bypasses storage and network; returns hardcoded mock config for dev/testing.
    /// Do NOT enable in production.
    private let useLocalMockConfig: Bool

    public init(storage: PulseSdkConfigStorage = PulseSdkConfigStorage(), useLocalMockConfig: Bool = false) {
        self.storage = storage
        self.useLocalMockConfig = useLocalMockConfig
    }

    /// Loads and returns the current config from persistence (sync). Returns nil if none stored or decode failed.
    /// On decode failure we do not crash; we return nil and log, then the app uses Pulse.initialize defaults.
    /// When useLocalMockConfig is true, returns PulseMockConfigProvider.fullMockConfig() and ignores storage.
    public func loadCurrentConfig() -> PulseSdkConfig? {
        if useLocalMockConfig {
            return PulseMockConfigProvider.fullMockConfig()
        }
        return storage.load()
    }

    /// Starts a background fetch for config and persists only when version changed (apply on next launch).
    /// Dispatchers.IO, fetch via PulseSdkConfigRestProvider, persist only if newConfig != null && newConfig.version != currentVersion.
    /// When useLocalMockConfig is true, skips network fetch entirely.
    /// - Parameters:
    ///   - configEndpointUrl: Final config URL (already resolved by PulseKit from endpointBaseUrl when nil; e.g. `{base:8080}/v1/configs/active/`).
    ///   - endpointHeaders: Headers sent with the GET request (e.g. X-API-KEY / api key).
    ///   - currentConfigVersion: Version of the config already loaded at init (avoids loading from storage again).
    public func startBackgroundFetch(
        configEndpointUrl: String,
        endpointHeaders: [String: String],
        currentConfigVersion: Int?
    ) {
        if useLocalMockConfig { return }
        let currentVersion = currentConfigVersion
        let configEndpointUrlFinal = configEndpointUrl
        let endpointHeadersForConfig = endpointHeaders
        let storageRef = storage

        // Async, non-blocking: work runs on a background thread (QoS .utility).
        Task.detached(priority: .utility) {
            guard let url = URL(string: configEndpointUrlFinal) else {
                PulseLogger.log("Config fetch: invalid config URL (skipping fetch) \(configEndpointUrlFinal)")
                return
            }
            let session = Self.makeSessionForConfigAPI()
            let provider = PulseSdkConfigRestProvider(
                urlProvider: { Optional(url) },
                urlSession: session,
                headers: endpointHeadersForConfig
            )
            let newConfig = await provider.provide()
            let shouldPersist = newConfig != nil && newConfig?.version != currentVersion
            let newStr = (newConfig?.version).map { "\($0)" } ?? "nil"
            let curStr = currentVersion.map { "\($0)" } ?? "nil"
            PulseLogger.log("Config fetch: newVersion=\(newStr) currentVersion=\(curStr) shouldUpdate=\(shouldPersist)")
            if shouldPersist, let config = newConfig {
                storageRef.saveSync(config)
            }
        }
    }

    // MARK: - Private

    private static func configApiCacheDirectory() -> URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("pulse", isDirectory: true)
            .appendingPathComponent("apiCache", isDirectory: true)
    }

    private static func makeSessionForConfigAPI() -> URLSession {
        guard let cacheDir = configApiCacheDirectory() else {
            return .shared
        }
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        return makeConfigURLSession(cacheDirectory: cacheDir)
    }
}
