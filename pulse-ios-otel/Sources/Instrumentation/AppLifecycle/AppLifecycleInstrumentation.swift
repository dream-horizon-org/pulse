/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Emits `device.app.lifecycle` log events when the app transitions between
/// created, foreground, and background states.
///
/// Log format:
///   event name: `device.app.lifecycle`
///   attribute:  `ios.app.state` = `"created"` | `"foreground"` | `"background"`
public final class AppLifecycleInstrumentation: AppStateListener {
    public static let eventName = "device.app.lifecycle"
    public static let appStateAttributeKey = "ios.app.state"

    private let logger: OpenTelemetryApi.Logger

    public init(logger: OpenTelemetryApi.Logger) {
        self.logger = logger
    }

    // MARK: - AppStateListener

    public func appCreated() {
        emitLifecycleLog(state: .created)
    }

    public func appForegrounded() {
        emitLifecycleLog(state: .foreground)
    }

    public func appBackgrounded() {
        emitLifecycleLog(state: .background)
    }

    // MARK: - Lifecycle

    public func uninstall() {
        AppStateWatcher.shared.removeListener(self)
        AppStateWatcher.shared.stop()
    }

    // MARK: - Private

    private func emitLifecycleLog(state: AppState) {
        logger.logRecordBuilder()
            .setEventName(Self.eventName)
            .setAttributes([
                Self.appStateAttributeKey: AttributeValue.string(state.rawValue)
            ])
            .emit()
    }
}
