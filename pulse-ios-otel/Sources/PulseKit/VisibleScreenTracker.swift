/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
#if os(iOS) || os(tvOS)
import UIKit
#endif

internal class VisibleScreenTracker {
    static let shared = VisibleScreenTracker()

    private var currentViewController: String?
    private var _previouslyVisibleScreen: String?
    private var isFirstScreen: Bool = true
    /// Tracks VC appearance order so we can restore `currentViewController`
    /// when a pageSheet modal is dismissed (underlying VC won't get viewDidAppear).
    private var screenStack: [String] = []
    private let queue = DispatchQueue(label: "com.pulse.ios.sdk.visiblescreen")

    private var tracer: Tracer?

    /// When true, emits Created/Restarted/Stopped/ViewControllerSession spans.
    /// Controlled by `ScreenLifecycleInstrumentationConfig.enabled`.
    private var _emitLifecycleSpans: Bool = false
    /// When true, calls `AppStartupTimer.shared.end()` on first screen appearance.
    /// Controlled by `AppStartupInstrumentationConfig.enabled`.
    private var _emitAppStartup: Bool = false

    /// Opened at viewDidLoad (first load) or viewWillAppear (subsequent appearances).
    /// Closed at viewDidAppear. Span name: "Created".
    /// Events: ViewDidLoad → ViewWillAppear → ViewIsAppearing → ViewDidAppear.
    /// Gets pulse.type = screen_load.
    private var appearingSpan: Span?
    private var appearingScreenName: String?

    /// Opened in viewWillDisappear, closed in viewDidDisappear. Span name: "Stopped".
    /// Events: ViewWillDisappear → ViewDidDisappear.
    private var disappearingSpan: Span?
    private var disappearingScreenName: String?

    /// Opened in viewDidAppear, closed in viewWillDisappear.
    /// Measures time user spent on screen. Gets pulse.type = screen_session.
    private var sessionSpan: Span?

    private init() {}

    /// Stores the tracer. Idempotent — safe to call multiple times.
    func start(tracer: Tracer) {
        queue.sync {
            if self.tracer == nil {
                self.tracer = tracer
            }
        }
    }

    /// Enable lifecycle span emission (Created/Restarted/Stopped/ViewControllerSession).
    func enableLifecycleSpans() {
        queue.sync { _emitLifecycleSpans = true }
    }

    /// Enable AppStartupTimer.end() call on first screen appearance.
    func enableAppStartup() {
        queue.sync { _emitAppStartup = true }
    }

    var currentlyVisibleScreen: String {
        return queue.sync { currentViewController ?? "unknown" }
    }

    var previouslyVisibleScreen: String? {
        return queue.sync { _previouslyVisibleScreen }
    }

    #if os(iOS) || os(tvOS)

    // MARK: - Swizzle Callbacks

    func viewControllerDidLoad(_ viewController: UIViewController) {
        guard queue.sync(execute: { _emitLifecycleSpans }) else { return }
        let screenName = String(describing: type(of: viewController))
        let capturedTracer: Tracer? = queue.sync { tracer }

        forceEndStaleAppearingSpan()
        startAppearingSpan(spanName: "Created", screenName: screenName, tracer: capturedTracer, firstEvent: "ViewDidLoad")
    }

    func viewControllerWillAppear(_ viewController: UIViewController) {
        guard queue.sync(execute: { _emitLifecycleSpans }) else { return }
        let screenName = String(describing: type(of: viewController))

        var capturedTracer: Tracer? = nil
        var spanAlreadyExists = false
        queue.sync {
            capturedTracer = self.tracer
            if self.appearingSpan != nil && self.appearingScreenName == screenName {
                self.appearingSpan?.addEvent(name: "ViewWillAppear")
                spanAlreadyExists = true
            }
        }

        if !spanAlreadyExists {
            forceEndStaleAppearingSpan()
            startAppearingSpan(spanName: "Restarted", screenName: screenName, tracer: capturedTracer, firstEvent: "ViewWillAppear")
        }
    }

    func viewControllerIsAppearing(_ viewController: UIViewController) {
        guard queue.sync(execute: { _emitLifecycleSpans }) else { return }
        let screenName = String(describing: type(of: viewController))
        queue.sync {
            guard let span = appearingSpan, appearingScreenName == screenName else { return }
            span.addEvent(name: "ViewIsAppearing")
        }
    }

    /// Ends AppStart span if enabled. Safe to call for any VC (including non-tracked ones
    /// like RCTRootViewController in React Native). Idempotent — only fires once.
    func endAppStartIfNeeded() {
        guard queue.sync(execute: { _emitAppStartup }) else { return }
        AppStartupTimer.shared.end()
    }

    func viewControllerDidAppear(_ viewController: UIViewController) {
        let screenName = String(describing: type(of: viewController))

        var capturedTracer: Tracer? = nil
        var emitLifecycle = false
        queue.sync { [weak self] in
            guard let self = self else { return }
            if let current = self.currentViewController, current != screenName {
                self._previouslyVisibleScreen = current
            }
            self.currentViewController = screenName
            if self.isFirstScreen {
                self.isFirstScreen = false
            }
            self.screenStack.removeAll { $0 == screenName }
            self.screenStack.append(screenName)
            capturedTracer = self.tracer
            emitLifecycle = self._emitLifecycleSpans
        }

        if emitLifecycle {
            normalEndAppearingSpan(screenName: screenName)
            endSessionSpan()
            startSessionSpan(screenName: screenName, tracer: capturedTracer)
        }
    }

    func viewControllerWillDisappear(_ viewController: UIViewController) {
        guard queue.sync(execute: { _emitLifecycleSpans }) else { return }
        let screenName = String(describing: type(of: viewController))
        let capturedTracer: Tracer? = queue.sync { tracer }

        let current = currentlyVisibleScreen
        if screenName == current {
            endSessionSpan()
        }

        forceEndStaleDisappearingSpan()
        startDisappearingSpan(screenName: screenName, tracer: capturedTracer)
    }

    func viewControllerDidDisappear(_ viewController: UIViewController) {
        let screenName = String(describing: type(of: viewController))

        if queue.sync(execute: { _emitLifecycleSpans }) {
            normalEndDisappearingSpan(screenName: screenName)
        }

        // Always maintain the screen stack regardless of lifecycle span config.
        // Needed for accurate screen.name tracking after pageSheet modal dismiss.
        queue.sync {
            screenStack.removeAll { $0 == screenName }
            if currentViewController == screenName {
                _previouslyVisibleScreen = currentViewController
                currentViewController = screenStack.last
            }
        }
    }

    #endif

    // MARK: - Created / Restarted Span → viewDidAppear = screen_load

    private func startAppearingSpan(spanName: String, screenName: String, tracer: Tracer?, firstEvent: String) {
        guard let tracer = tracer else { return }
        let previousScreen: String? = queue.sync { currentViewController }
        let span = tracer.spanBuilder(spanName: spanName)
            .setAttribute(key: PulseAttributes.viewControllerName, value: screenName)
            .setAttribute(key: PulseAttributes.screenName, value: screenName)
            .setNoParent()
            .startSpan()
        if let previousScreen = previousScreen {
            span.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreen))
        }
        span.addEvent(name: firstEvent)
        queue.sync {
            appearingSpan = span
            appearingScreenName = screenName
        }
    }

    /// Normal close: called from viewDidAppear. Adds ViewDidAppear event then ends.
    private func normalEndAppearingSpan(screenName: String) {
        let span: Span? = queue.sync {
            guard let s = appearingSpan, appearingScreenName == screenName else { return nil }
            appearingSpan = nil
            appearingScreenName = nil
            return s
        }
        guard let span = span else { return }
        span.addEvent(name: "ViewDidAppear")
        span.end()
    }

    /// Stale cleanup: called before starting a new appearing span.
    /// Ends without adding any event so it doesn't produce a misleading complete span.
    private func forceEndStaleAppearingSpan() {
        let span: Span? = queue.sync {
            guard let s = appearingSpan else { return nil }
            appearingSpan = nil
            appearingScreenName = nil
            return s
        }
        span?.end()
    }

    // MARK: - Stopped Span (viewWillDisappear → viewDidDisappear)

    private func startDisappearingSpan(screenName: String, tracer: Tracer?) {
        guard let tracer = tracer else { return }
        let previousScreen: String? = queue.sync { _previouslyVisibleScreen }
        let span = tracer.spanBuilder(spanName: "Stopped")
            .setAttribute(key: PulseAttributes.viewControllerName, value: screenName)
            .setAttribute(key: PulseAttributes.screenName, value: screenName)
            .setNoParent()
            .startSpan()
        if let previousScreen = previousScreen {
            span.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreen))
        }
        span.addEvent(name: "ViewWillDisappear")
        queue.sync {
            disappearingSpan = span
            disappearingScreenName = screenName
        }
    }

    /// Normal close: called from viewDidDisappear. Adds ViewDidDisappear event then ends.
    private func normalEndDisappearingSpan(screenName: String) {
        let span: Span? = queue.sync {
            guard let s = disappearingSpan, disappearingScreenName == screenName else { return nil }
            disappearingSpan = nil
            disappearingScreenName = nil
            return s
        }
        guard let span = span else { return }
        span.addEvent(name: "ViewDidDisappear")
        span.end()
    }

    /// Stale cleanup: called before starting a new disappearing span.
    /// Ends without adding any event so it doesn't produce a misleading complete span.
    private func forceEndStaleDisappearingSpan() {
        let span: Span? = queue.sync {
            guard let s = disappearingSpan else { return nil }
            disappearingSpan = nil
            disappearingScreenName = nil
            return s
        }
        span?.end()
    }

    // MARK: - Session Span (viewDidAppear → viewWillDisappear)

    private func startSessionSpan(screenName: String, tracer: Tracer?) {
        guard let tracer = tracer else { return }
        let previousScreen: String? = queue.sync { _previouslyVisibleScreen }
        let span = tracer.spanBuilder(spanName: "ViewControllerSession")
            .setAttribute(key: PulseAttributes.viewControllerName, value: screenName)
            .setAttribute(key: PulseAttributes.screenName, value: screenName)
            .setNoParent()
            .startSpan()
        if let previousScreen = previousScreen {
            span.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreen))
        }
        queue.sync { sessionSpan = span }
    }

    private func endSessionSpan() {
        let previous: Span? = queue.sync {
            let s = sessionSpan
            sessionSpan = nil
            return s
        }
        if let span = previous {
            span.end()
        }
    }
}

