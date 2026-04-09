/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if os(iOS) || os(tvOS)
import UIKit
#endif

/// Singleton service that observes UIApplication lifecycle notifications and
/// forwards them to registered `AppStateListener` instances.
/// This is a service layer — it does not emit OTel signals itself.
public final class AppStateWatcher {
    public static let shared = AppStateWatcher()

    private let lock = NSLock()
    private var listeners: [WeakListener] = []
    public private(set) var currentState: AppState = .created

    private init() {}

    // MARK: - Public API

    public func registerListener(_ listener: AppStateListener) {
        lock.lock()
        defer { lock.unlock() }
        listeners.removeAll { $0.value == nil }
        guard !listeners.contains(where: { $0.value === listener }) else { return }
        listeners.append(WeakListener(listener))
    }

    public func removeListener(_ listener: AppStateListener) {
        lock.lock()
        defer { lock.unlock() }
        listeners.removeAll { $0.value === listener || $0.value == nil }
    }

    /// Begin observing NotificationCenter for UIApplication lifecycle events.
    /// Called once during SDK initialization.
    public func start() {
        #if os(iOS) || os(tvOS)
        let nc = NotificationCenter.default

        nc.addObserver(
            self,
            selector: #selector(handleDidFinishLaunching),
            name: UIApplication.didFinishLaunchingNotification,
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )

        // If the app is already active when SDK initializes (common),
        // fire created immediately so listeners don't miss the initial state.
        if UIApplication.shared.applicationState != .background {
            notifyCreated()
        }
        #endif
    }

    /// Stop observing. Called on SDK shutdown.
    public func stop() {
        #if os(iOS) || os(tvOS)
        NotificationCenter.default.removeObserver(self)
        #endif
    }

    // MARK: - Notification Handlers

    #if os(iOS) || os(tvOS)
    @objc private func handleDidFinishLaunching() {
        notifyCreated()
    }

    @objc private func handleWillEnterForeground() {
        lock.lock()
        currentState = .foreground
        let snapshot = listeners.compactMap { $0.value }
        lock.unlock()
        for listener in snapshot {
            listener.appForegrounded()
        }
    }

    @objc private func handleDidEnterBackground() {
        lock.lock()
        currentState = .background
        let snapshot = listeners.compactMap { $0.value }
        lock.unlock()
        for listener in snapshot {
            listener.appBackgrounded()
        }
    }
    #endif

    // MARK: - Helpers

    private func notifyCreated() {
        lock.lock()
        guard currentState == .created else {
            lock.unlock()
            return
        }
        currentState = .foreground
        let snapshot = listeners.compactMap { $0.value }
        lock.unlock()
        for listener in snapshot {
            listener.appCreated()
        }
    }
}

// MARK: - WeakListener wrapper

private struct WeakListener {
    weak var value: AppStateListener?
    init(_ value: AppStateListener) { self.value = value }
}
