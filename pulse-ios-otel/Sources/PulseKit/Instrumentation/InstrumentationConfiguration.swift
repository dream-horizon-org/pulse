/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

public struct InstrumentationConfiguration {
    private var _urlSession: URLSessionInstrumentationConfig = URLSessionInstrumentationConfig()
    private var _sessions: SessionsInstrumentationConfig = SessionsInstrumentationConfig()
    private var _signPost: SignPostInstrumentationConfig = SignPostInstrumentationConfig()
    private var _interaction: InteractionInstrumentationConfig = InteractionInstrumentationConfig()
    private var _location: LocationInstrumentationConfig = LocationInstrumentationConfig()
    private var _crash: CrashInstrumentationConfig = CrashInstrumentationConfig()
    private var _appLifecycle: AppLifecycleInstrumentationConfig = AppLifecycleInstrumentationConfig()
    private var _screenLifecycle: ScreenLifecycleInstrumentationConfig = ScreenLifecycleInstrumentationConfig()
    private var _appStartup: AppStartupInstrumentationConfig = AppStartupInstrumentationConfig()
    private var _uiKitTap: UIKitTapInstrumentationConfig = UIKitTapInstrumentationConfig()
    private var _sessionReplay: SessionReplayInstrumentationConfig = SessionReplayInstrumentationConfig()

    public init() {}

    public mutating func urlSession(_ configure: (inout URLSessionInstrumentationConfig) -> Void) {
        configure(&_urlSession)
    }

    public mutating func sessions(_ configure: (inout SessionsInstrumentationConfig) -> Void) {
        configure(&_sessions)
    }

    public mutating func signPost(_ configure: (inout SignPostInstrumentationConfig) -> Void) {
        configure(&_signPost)
    }

    public mutating func interaction(_ configure: (inout InteractionInstrumentationConfig) -> Void) {
        configure(&_interaction)
    }

    public mutating func location(_ configure: (inout LocationInstrumentationConfig) -> Void) {
        configure(&_location)
    }

    public mutating func crash(_ configure: (inout CrashInstrumentationConfig) -> Void) {
        configure(&_crash)
    }

    public mutating func appLifecycle(_ configure: (inout AppLifecycleInstrumentationConfig) -> Void) {
        configure(&_appLifecycle)
    }

    public mutating func screenLifecycle(_ configure: (inout ScreenLifecycleInstrumentationConfig) -> Void) {
        configure(&_screenLifecycle)
    }

    public mutating func appStartup(_ configure: (inout AppStartupInstrumentationConfig) -> Void) {
        configure(&_appStartup)
    }

    public mutating func uiKitTap(_ configure: (inout UIKitTapInstrumentationConfig) -> Void) {
        configure(&_uiKitTap)
    }

    public mutating func sessionReplay(_ configure: (inout SessionReplayInstrumentationConfig) -> Void) {
        configure(&_sessionReplay)
    }

    internal mutating func attachSessionReplayConsentFromPulse(
        isCaptureAllowed: @escaping () -> Bool,
        startActiveAtInstall: Bool
    ) {
        _sessionReplay.attachPulseSessionReplayConsent(
            isCaptureAllowed: isCaptureAllowed,
            startActiveAtInstall: startActiveAtInstall
        )
    }

    internal var urlSession: URLSessionInstrumentationConfig { _urlSession }
    internal var sessions: SessionsInstrumentationConfig { _sessions }
    internal var signPost: SignPostInstrumentationConfig { _signPost }
    internal var interaction: InteractionInstrumentationConfig { _interaction }
    internal var location: LocationInstrumentationConfig { _location }
    internal var crash: CrashInstrumentationConfig { _crash }
    internal var appLifecycle: AppLifecycleInstrumentationConfig { _appLifecycle }
    internal var screenLifecycle: ScreenLifecycleInstrumentationConfig { _screenLifecycle }
    internal var appStartup: AppStartupInstrumentationConfig { _appStartup }
    internal var uiKitTap: UIKitTapInstrumentationConfig { _uiKitTap }
    internal var sessionReplay: SessionReplayInstrumentationConfig { _sessionReplay }

    internal var instrumentations: [InstrumentationLifecycle] {
        [
            _urlSession,
            _sessions,
            _signPost,
            _interaction,
            _location,
            _crash,
            _appLifecycle,
            _screenLifecycle,
            _appStartup,
            _uiKitTap,
            _sessionReplay
        ]
    }
}
