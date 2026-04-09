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
import QuartzCore
public class SessionReplayRecorder {
    private static let recorderQueueSpecificKey = DispatchSpecificKey<UInt8>()
    private static let recorderQueueMarker: UInt8 = 1

    private let config: SessionReplayConfig
    private let capturer: SessionReplayCapturer
    private var _isRecording: Bool = false
    private let recordingStateLock = NSLock()  // Protects recording state without blocking
    private let queue = DispatchQueue(label: "com.pulse.ios.sdk.sessionreplay", qos: .utility)
    private var lifecycleObservers: [NSObjectProtocol] = []
    private var onDrawFlag: Bool = false
    private var displayLink: CADisplayLink?
    private let drawFlagLock = NSLock()
    private var _throttler: SessionReplayThrottler?
    private let throttlerLock = NSLock()  // Synchronizes throttler access across threads
    private var drawOccurredSinceLastCapture: Bool = false
    private let drawOccurredLock = NSLock()
    private var windowStatuses: [UIWindow: WindowSnapshotStatus] = [:]
    private let windowStatusLock = NSLock()
    private var persistingEmitter: SessionReplayPersistingEmitter?
    private let transformer = SessionReplayEventTransformer()
    private let projectId: String?
    private let userIdProvider: (() -> String?)?
    private var currentSessionId: String?
    private let isSessionReplayCaptureAllowed: () -> Bool
    /// After backgrounding or consent `.pending`, resume without resetting wireframe/snapshot state.
    private var needsResumeAfterInactive: Bool = false

    public init(
        config: SessionReplayConfig,
        exporter: SessionReplayExporter? = nil,
        isSessionReplayCaptureAllowed: @escaping () -> Bool = { true },
        deferSendCachedEventsUntilAllowed: Bool = false
    ) {
        self.config = config
        self.capturer = ScreenshotCapturer(config: config)
        self.projectId = exporter?.projectId
        self.userIdProvider = exporter?.userIdProvider
        self.isSessionReplayCaptureAllowed = isSessionReplayCaptureAllowed

        if let exporter = exporter {
            let startFlushTimer = !deferSendCachedEventsUntilAllowed
            self.persistingEmitter = SessionReplayPersistingEmitter(
                transport: exporter.transport,
                flushIntervalSeconds: config.effectiveFlushIntervalSeconds,
                flushAt: config.effectiveFlushAt,
                maxBatchSize: config.effectiveMaxBatchSize,
                startFlushTimer: startFlushTimer
            )
            if !deferSendCachedEventsUntilAllowed {
                self.persistingEmitter?.sendCachedEvents()
            }
        }

        queue.setSpecific(key: Self.recorderQueueSpecificKey, value: Self.recorderQueueMarker)

        setupLifecycleObservers()
    }

    private func executeOnRecorderQueueSync(_ work: () -> Void) {
        if DispatchQueue.getSpecific(key: Self.recorderQueueSpecificKey) == Self.recorderQueueMarker {
            work()
        } else {
            queue.sync(execute: work)
        }
    }

    deinit {
        #if os(iOS) || os(tvOS)
        if let link = displayLink {
            displayLink = nil
            if Thread.isMainThread {
                link.invalidate()
            } else {
                DispatchQueue.main.sync {
                    link.invalidate()
                }
            }
        }
        #endif
        removeLifecycleObservers()
    }
    
    // MARK: - Recording State (Non-blocking Access)
    
    /// Returns recording state without blocking. Uses lock instead of queue.sync to prevent deadlock.
    public var isRecording: Bool {
        recordingStateLock.lock()
        defer { recordingStateLock.unlock() }
        return _isRecording
    }
    
    /// Thread-safe throttler access to prevent data races between main thread and recorder queue.
    private var throttler: SessionReplayThrottler? {
        get {
            throttlerLock.lock()
            defer { throttlerLock.unlock() }
            return _throttler
        }
        set {
            throttlerLock.lock()
            defer { throttlerLock.unlock() }
            _throttler = newValue
        }
    }
    
    // MARK: - User ID Resolution
    
    private func resolvedUserId() -> String {
        userIdProvider?() ?? "anonymous"
    }
    
    public func start(resetState: Bool = true) {
        queue.async { [weak self] in
            guard let self = self else { return }

            self.needsResumeAfterInactive = false

            // Update recording state safely
            self.recordingStateLock.lock()
            guard !self._isRecording else {
                self.recordingStateLock.unlock()
                return
            }
            self._isRecording = true
            self.recordingStateLock.unlock()
            
            if resetState {
                self.transformer.reset()
                self.resetWindowStatuses()
                self.currentSessionId = nil
            }
            
            // Set throttler safely
            self.throttler = SessionReplayThrottler(throttleDelayMs: self.config.effectiveCaptureIntervalMs, queue: self.queue)
            self.resetDrawOccurredFlag()
            self.startDisplayLink()
        }
    }
    
    public func resume() {
        start(resetState: false)
    }
    
    /// Stops capture and shuts down the persisting emitter (SDK uninstall / consent denied).
    public func tearDown() {
        executeOnRecorderQueueSync { [self] in
            needsResumeAfterInactive = false
            recordingStateLock.lock()
            _isRecording = false
            recordingStateLock.unlock()
            throttler?.cancel()
            throttler = nil
            stopDisplayLink()
            persistingEmitter?.shutdown()
        }
    }

    /// Pauses capture and periodic upload timer; keeps on-disk batches for a later `.allowed` transition.
    public func pauseCapturing() {
        queue.async { [weak self] in
            guard let self = self else { return }
            self.needsResumeAfterInactive = true
            self.recordingStateLock.lock()
            self._isRecording = false
            self.recordingStateLock.unlock()
            self.throttler?.cancel()
            self.throttler = nil
            self.stopDisplayLink()
            self.persistingEmitter?.pauseFlushTimer()
        }
    }

    /// After consent moves to `.allowed`, flush disk cache, restart upload timer, and resume capture without resetting snapshot state.
    public func resumeAfterConsentGrant() {
        persistingEmitter?.flush()
        persistingEmitter?.sendCachedEvents()
        persistingEmitter?.resumeFlushTimer()
        resume()
    }

    public func flushPersisting() {
        persistingEmitter?.flush()
    }

    /// Same as `tearDown()`.
    public func stop() {
        tearDown()
    }

    private func resetDrawOccurredFlag() {
        drawOccurredLock.lock()
        drawOccurredSinceLastCapture = false
        drawOccurredLock.unlock()
    }
    
    /// Schedules capture on the main queue so UIKit/window access never uses `DispatchQueue.main.sync` from the recorder utility queue (avoids deadlock with `Pulse.shutdown()` / `initializationQueue.sync` on the main thread).
    private func captureFrame(completion: @escaping (SessionReplayFrame?) -> Void) {
        #if os(iOS) || os(tvOS)
        let run = { [weak self] in
            guard let self = self else {
                completion(nil)
                return
            }
            self.performCaptureFrameOnMainThread(completion: completion)
        }
        if Thread.isMainThread {
            run()
        } else {
            DispatchQueue.main.async(execute: run)
        }
        #else
        completion(nil)
        #endif
    }

    #if os(iOS) || os(tvOS)
    /// Must run on the main thread. Performs window enumeration, draw-flag setup, masking checker wiring, and kicks off `capturer.capture` (masking pipeline still runs on main + global per `ScreenshotCapturer`).
    private func performCaptureFrameOnMainThread(completion: @escaping (SessionReplayFrame?) -> Void) {
        assert(Thread.isMainThread)

        let windows = getAllVisibleWindows()

        guard !windows.isEmpty else {
            completion(nil)
            return
        }

        resetDrawFlag()

        if let masker = (capturer as? ScreenshotCapturer)?.masker {
            masker.setDrawFlagChecker { [weak self] in
                self?.checkDrawFlag() ?? false
            }
        }

        let keyWindows = windows.filter { $0.isKeyWindow }
        let windowsToCapture = keyWindows.isEmpty ? windows : keyWindows

        let session = SessionManagerProvider.getInstance().getSession()
        let frameSessionId = session.id

        if currentSessionId != frameSessionId {
            currentSessionId = frameSessionId
        }

        let windowsAndNames: [(UIWindow, String)] = windowsToCapture.map { window in
            (window, getCurrentScreenName(from: window))
        }

        let dispatchGroup = DispatchGroup()
        var capturedFrames: [SessionReplayFrame] = []
        var allEvents: [SessionReplayEvent] = []
        let framesLock = NSLock()
        let eventsLock = NSLock()

        for (window, screenName) in windowsAndNames {
            dispatchGroup.enter()

            capturer.capture(window: window, scale: config.effectiveScreenshotScale) { [weak self] capturedImage in
                defer { dispatchGroup.leave() }

                guard let self = self, let capturedImage = capturedImage else {
                    return
                }

                guard let compressed = SessionReplayCompressor.compress(
                    image: capturedImage,
                    quality: self.config.effectiveCompressionQuality
                ) else {
                    return
                }

                let imgW = Int(capturedImage.size.width)
                let imgH = Int(capturedImage.size.height)

                let frame = SessionReplayFrame(
                    timestamp: Date(),
                    sessionId: frameSessionId,
                    screenName: screenName,
                    imageData: compressed.data,
                    format: compressed.format,
                    width: imgW,
                    height: imgH
                )

                framesLock.lock()
                capturedFrames.append(frame)
                framesLock.unlock()

                if self.persistingEmitter != nil, let pid = self.projectId {
                    var windowStatus = self.getWindowStatus(window: window)
                    let events = self.transformer.transformFrame(
                        frame: frame,
                        windowStatus: &windowStatus,
                        projectId: pid,
                        userId: self.resolvedUserId(),
                        appVersion: self.getAppVersion(),
                        aspectRatio: self.getCurrentAspectRatio()
                    )
                    self.updateWindowStatus(window: window, status: windowStatus)

                    eventsLock.lock()
                    allEvents.append(contentsOf: events)
                    eventsLock.unlock()
                }
            }
        }

        dispatchGroup.notify(queue: queue) { [weak self] in
            guard let self = self else {
                completion(nil)
                return
            }

            guard !capturedFrames.isEmpty else {
                completion(nil)
                return
            }

            if let emitter = self.persistingEmitter, let pid = self.projectId, !allEvents.isEmpty, let sessionId = self.currentSessionId {
                let payload = SessionReplayPayload(
                    projectId: pid,
                    userId: self.resolvedUserId(),
                    properties: SessionReplayProperties(
                        sessionId: sessionId,
                        snapshotSource: "iOS",
                        snapshotData: allEvents,
                        appVersion: self.getAppVersion()
                    )
                )

                if let jsonData = try? JSONEncoder().encode(payload),
                   let jsonString = String(data: jsonData, encoding: .utf8) {
                    emitter.emit(payloadJson: jsonString)
                }
            }

            completion(capturedFrames.first)
        }
    }
    #endif
    
    private func getAllVisibleWindows() -> [UIWindow] {
        #if os(iOS) || os(tvOS)
        assert(Thread.isMainThread, "getAllVisibleWindows must run on the main thread")

        var visibleWindows: [UIWindow] = []
        
        if #available(iOS 15.0, *) {
            for scene in UIApplication.shared.connectedScenes {
                if let windowScene = scene as? UIWindowScene {
                    for window in windowScene.windows {
                        let windowClassName = String(describing: type(of: window))
                        if windowClassName.contains("RemoteKeyboardWindow") ||
                           windowClassName.contains("UITextEffectsWindow") ||
                           windowClassName.contains("UIRemoteKeyboardWindow") ||
                           windowClassName.contains("StatusBar") {
                            continue
                        }
                        
                        if window.isHidden == false && window.alpha > 0 && window.windowScene != nil {
                            let isKey = window.isKeyWindow
                            if window.rootViewController != nil || isKey {
                                visibleWindows.append(window)
                            }
                        }
                    }
                }
            }
        } else {
            for window in UIApplication.shared.windows {
                let windowClassName = String(describing: type(of: window))
                if windowClassName.contains("RemoteKeyboardWindow") ||
                   windowClassName.contains("UITextEffectsWindow") ||
                   windowClassName.contains("UIRemoteKeyboardWindow") ||
                   windowClassName.contains("StatusBar") {
                    continue
                }
                
                if window.isHidden == false && window.alpha > 0 {
                    let isKey = window.isKeyWindow
                    if window.rootViewController != nil || isKey {
                        visibleWindows.append(window)
                    }
                }
            }
        }
        
        visibleWindows.sort { first, second in
            let firstIsKey = first.isKeyWindow
            let secondIsKey = second.isKeyWindow
            if firstIsKey && !secondIsKey {
                return true
            }
            if !firstIsKey && secondIsKey {
                return false
            }
            return first.windowLevel.rawValue > second.windowLevel.rawValue
        }
        
        return visibleWindows
        #else
        return []
        #endif
    }
    
    private func getWindowStatus(window: UIWindow) -> WindowSnapshotStatus {
        windowStatusLock.lock()
        defer { windowStatusLock.unlock() }
        return windowStatuses[window] ?? WindowSnapshotStatus()
    }
    
    private func updateWindowStatus(window: UIWindow, status: WindowSnapshotStatus) {
        windowStatusLock.lock()
        defer { windowStatusLock.unlock() }
        windowStatuses[window] = status
    }
    
    private func resetWindowStatuses() {
        windowStatusLock.lock()
        defer { windowStatusLock.unlock() }
        for (window, _) in windowStatuses {
            windowStatuses[window] = WindowSnapshotStatus()
        }
    }
    
    #if os(iOS) || os(tvOS)
    private func getCurrentScreenName(from window: UIWindow) -> String {
        return VisibleScreenTracker.shared.currentlyVisibleScreen
    }
    
    private func getAppVersion() -> String? {
        if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
           !version.isEmpty {
            return version
        }
        return nil
    }
    
    private func getCurrentAspectRatio() -> String? {
        #if os(iOS) || os(tvOS)
        var aspectRatio: String?
        DispatchQueue.main.sync {
            aspectRatio = PulseUtils.currentViewportAspectRatio()
        }
        return aspectRatio
        #else
        return nil
        #endif
    }

    #endif
    
    private func setupLifecycleObservers() {
        #if os(iOS) || os(tvOS)
        let center = NotificationCenter.default
        
        let didBecomeActive = center.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            guard let self = self else { return }
            self.queue.async { [weak self] in
                guard let self = self else { return }
                guard self.isSessionReplayCaptureAllowed() else { return }
                if self.needsResumeAfterInactive {
                    self.needsResumeAfterInactive = false
                    self.start(resetState: false)
                    return
                }
                self.recordingStateLock.lock()
                let recording = self._isRecording
                self.recordingStateLock.unlock()
                if !recording {
                    self.start(resetState: true)
                }
            }
        }
        lifecycleObservers.append(didBecomeActive)

        let willResignActive = center.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.persistingEmitter?.flush()
        }
        lifecycleObservers.append(willResignActive)

        let didEnterBackground = center.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.persistingEmitter?.flush()
            self?.pauseCapturing()
        }
        lifecycleObservers.append(didEnterBackground)
        #endif
    }
    
    private func removeLifecycleObservers() {
        lifecycleObservers.forEach { NotificationCenter.default.removeObserver($0) }
        lifecycleObservers.removeAll()
    }
    
    private func startDisplayLink() {
        #if os(iOS) || os(tvOS)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.recordingStateLock.lock()
            let recording = self._isRecording
            self.recordingStateLock.unlock()
            guard recording else { return }

            let link = CADisplayLink(target: self, selector: #selector(self.displayLinkFired))
            link.add(to: .main, forMode: .common)
            self.displayLink = link
        }
        #endif
    }

    /// Invalidates on the main run loop without `main.sync` from the recorder queue (prevents deadlock when main is blocked in `Pulse.shutdown` → `initializationQueue.sync`).
    private func stopDisplayLink() {
        #if os(iOS) || os(tvOS)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.displayLink?.invalidate()
            self.displayLink = nil
        }
        #endif
    }
    
    @objc private func displayLinkFired() {
        // Early guard: don't process if we've stopped recording
        // This closes the race window between stop() and display link invalidation
        guard isRecording else { return }
        
        drawFlagLock.lock()
        onDrawFlag = true
        drawFlagLock.unlock()
        
        drawOccurredLock.lock()
        drawOccurredSinceLastCapture = true
        drawOccurredLock.unlock()
        
        throttler?.throttle { [weak self] in
            guard let self = self else { return }
            
            self.drawOccurredLock.lock()
            let shouldCapture = self.drawOccurredSinceLastCapture
            if shouldCapture {
                self.drawOccurredSinceLastCapture = false
            }
            self.drawOccurredLock.unlock()
            
            if shouldCapture {
                self.captureFrame { _ in }
            }
        }
    }
    
    private func resetDrawFlag() {
        drawFlagLock.lock()
        onDrawFlag = false
        drawFlagLock.unlock()
    }
    
    private func checkDrawFlag() -> Bool {
        drawFlagLock.lock()
        let flag = onDrawFlag
        drawFlagLock.unlock()
        return flag
    }
}
#else
public class SessionReplayRecorder {
    private let config: SessionReplayConfig

    public init(
        config: SessionReplayConfig,
        exporter: SessionReplayExporter? = nil,
        isSessionReplayCaptureAllowed: @escaping () -> Bool = { true },
        deferSendCachedEventsUntilAllowed: Bool = false
    ) {
        self.config = config
    }

    public func start(resetState: Bool = true) {}
    public func resume() {}
    public func tearDown() {}
    public func pauseCapturing() {}
    public func resumeAfterConsentGrant() {}
    public func flushPersisting() {}
    public func stop() {}
    public var isRecording: Bool { return false }
}
#endif
