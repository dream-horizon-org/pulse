/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if canImport(Sessions)
import Sessions
#endif
#if os(iOS) || os(tvOS)
import UIKit
#endif

public class SessionReplayInstrumentation {
    private static let singletonLock: NSLock = NSLock()
    private static var sharedInstance: SessionReplayInstrumentation?

    public static func getInstance() -> SessionReplayInstrumentation? {
        singletonLock.lock()
        defer { singletonLock.unlock() }
        return sharedInstance
    }

    private var recorder: SessionReplayRecorder?
    private let config: SessionReplayConfig
    private let exporter: SessionReplayExporter?
    private let isSessionReplayCaptureAllowed: () -> Bool

    public init(
        config: SessionReplayConfig,
        exporter: SessionReplayExporter? = nil,
        isSessionReplayCaptureAllowed: @escaping () -> Bool = { true }
    ) {
        self.config = config
        self.exporter = exporter
        self.isSessionReplayCaptureAllowed = isSessionReplayCaptureAllowed

        SessionReplayInstrumentation.singletonLock.lock()
        defer { SessionReplayInstrumentation.singletonLock.unlock() }
        precondition(
            SessionReplayInstrumentation.sharedInstance == nil,
            "SessionReplayInstrumentation already exists. Call uninstall() on the existing instance before creating another."
        )
        SessionReplayInstrumentation.sharedInstance = self
    }

    /// Installs replay. When `shouldStartActive` is false (consent `.pending` at init), capture and cached upload wait until `.allowed`.
    public func install(shouldStartActive: Bool) {
        guard recorder == nil else { return }

        let recorder = SessionReplayRecorder(
            config: config,
            exporter: exporter,
            isSessionReplayCaptureAllowed: isSessionReplayCaptureAllowed,
            deferSendCachedEventsUntilAllowed: !shouldStartActive
        )
        self.recorder = recorder

        #if os(iOS) || os(tvOS)
        guard shouldStartActive, UIApplication.shared.applicationState == .active else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            guard let self = self, self.isSessionReplayCaptureAllowed() else { return }
            self.recorder?.start(resetState: true)
        }
        #endif
    }

    /// Consent moved `.allowed` → `.pending`: pause screenshots and periodic flush; keep on-disk batches.
    public func pauseForConsent() {
        recorder?.pauseCapturing()
    }

    /// Consent moved `.pending` → `.allowed`: resume capture with prior snapshot state; flush backlog.
    public func resumeAfterConsent() {
        recorder?.resumeAfterConsentGrant()
    }

    public func flushForShutdown() {
        recorder?.flushPersisting()
    }

    public func uninstall() {
        recorder?.tearDown()
        recorder = nil
        SessionReplayInstrumentation.singletonLock.lock()
        SessionReplayInstrumentation.sharedInstance = nil
        SessionReplayInstrumentation.singletonLock.unlock()
    }

    public var recorderInstance: SessionReplayRecorder? {
        recorder
    }
}
