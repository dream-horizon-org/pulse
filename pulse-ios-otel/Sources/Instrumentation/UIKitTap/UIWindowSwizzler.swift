/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if os(iOS) || os(tvOS)
import UIKit
import ObjectiveC
import QuartzCore
import OpenTelemetryApi

internal class UIWindowSwizzler {
    private static var swizzled = false
    private static let swizzleLock = NSLock()
    private static var logger: OpenTelemetryApi.Logger?
    private static var captureContext: Bool = true
    private static var rageConfig: RageConfig = RageConfig()
    
    private static var buffer: ClickEventBuffer?
    private static var emitter: ClickEventEmitter?
    private static var appLifecycleObserver: NSObjectProtocol?
    
    // Label extraction constants 
    private static let maxLabelSegments = 5
    private static let maxLabelLength = 200
    private static let labelDelimiter = " | "
    private static let maxLabelSearchDepth = 4

    // Scroll vs tap detection: if a touch moves more than this many points from
    // its start position, it is treated as a scroll/pan and NOT reported as a tap.
    private static let tapSlopDistance: CGFloat = 10
    // Touch start positions keyed by UITouch identity — always accessed on main thread.
    private static var touchStartLocations: [ObjectIdentifier: CGPoint] = [:]

    static func swizzle(logger: OpenTelemetryApi.Logger, captureContext: Bool, rageConfig: RageConfig) {
        swizzleLock.lock()
        defer { swizzleLock.unlock() }
        guard !swizzled else { return }
        Self.logger = logger
        Self.captureContext = captureContext
        Self.rageConfig = rageConfig

        emitter = ClickEventEmitter(logger: logger)
        buffer = ClickEventBuffer(
            rageConfig: rageConfig,
            onRage: { emitter?.emitRageClick($0) },
            onEmit: { [weak emitter] click in
                if click.hasTarget {
                    emitter?.emitGoodClick(click)
                } else {
                    emitter?.emitDeadClick(click)
                }
            }
        )

        if appLifecycleObserver == nil {
            registerForAppLifecycle()
        }
        swizzleSendEvent()
        swizzled = true
    }

    private static func swizzleSendEvent() {
        guard let method = class_getInstanceMethod(
            UIWindow.self,
            #selector(UIWindow.sendEvent(_:))
        ) else { return }

        var originalIMP: IMP?

        let block: @convention(block) (UIWindow, UIEvent) -> Void = { window, event in

            guard event.type == .touches, let touches = event.allTouches else {
                if let imp = originalIMP {
                    let fn = unsafeBitCast(imp, to: (@convention(c) (UIWindow, Selector, UIEvent) -> Void).self)
                    fn(window, #selector(UIWindow.sendEvent(_:)), event)
                }
                return
            }

            // Track touch start positions for scroll vs tap detection.
            for touch in touches where touch.phase == .began {
                touchStartLocations[ObjectIdentifier(touch)] = touch.location(in: window)
            }
            // Clean up cancelled touches (system interruption, incoming call, etc.)
            for touch in touches where touch.phase == .cancelled {
                touchStartLocations.removeValue(forKey: ObjectIdentifier(touch))
            }

            let clickTarget: (view: UIView?, location: CGPoint)? = {
                guard let touch = touches.first(where: { $0.phase == .ended }) else { return nil }
                let endLocation = touch.location(in: window)
                let key = ObjectIdentifier(touch)
                defer { touchStartLocations.removeValue(forKey: key) }

                // Scroll vs tap guard
                if let startLocation = touchStartLocations[key] {
                    let dx = endLocation.x - startLocation.x
                    let dy = endLocation.y - startLocation.y
                    let distSq = dx * dx + dy * dy
                    guard distSq <= tapSlopDistance * tapSlopDistance else {
                        return nil
                    }
                }

                let target = findClickTarget(in: window, at: endLocation)

                return (target, endLocation)
            }()

            // Dispatch the original touch event
            if let imp = originalIMP {
                let fn = unsafeBitCast(imp, to: (@convention(c) (UIWindow, Selector, UIEvent) -> Void).self)
                fn(window, #selector(UIWindow.sendEvent(_:)), event)
            }

            // Emit after dispatch so touch responsiveness is not affected
            if let (target, location) = clickTarget {
                emitClickEvent(target: target, at: location, in: window)
            }
        }

        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }
    
    private static func registerForAppLifecycle() {
        appLifecycleObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { _ in
            buffer?.flush()
        }
    }

    // MARK: - Click Event Emission

    private static func emitClickEvent(target: UIView?, at point: CGPoint, in window: UIWindow) {
        let widgetName = target.map { String(describing: type(of: $0)) } ?? ""
        let widgetId = target?.accessibilityIdentifier ?? ""
        
        let label: String? = captureContext && target != nil ? extractLabel(from: target!) : nil
        let context = label.flatMap(PulseAttributes.AppClickContext.buildContext)
        
        let pending = PendingClick(
            x: Float(point.x),
            y: Float(point.y),
            timestampMs: Int64(CACurrentMediaTime() * 1000), // monotonic clock for buffer timing
            tapEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            hasTarget: target != nil,
            widgetName: widgetName.isEmpty ? nil : widgetName,
            widgetId: widgetId.isEmpty ? nil : widgetId,
            clickContext: context,
            viewportWidthPt: Int(window.bounds.width),
            viewportHeightPt: Int(window.bounds.height)
        )
        
        buffer?.record(pending)
    }

    // MARK: - Hit Testing

    private static func findClickTarget(in window: UIWindow, at point: CGPoint) -> UIView? {
        guard let hitView = window.hitTest(point, with: nil) else { return nil }
        // Walk up to find the most meaningful interactable ancestor (same pipeline for UIKit and SwiftUI-backed UI).
        var candidate: UIView? = hitView
        while let view = candidate {
            if isClickTarget(view) { return view }
            candidate = view.superview
        }
        return nil
    }

    internal static func isClickTarget(_ view: UIView) -> Bool {
        if view is UIWindow { return false }
        if view is UIScrollView { return false }
        if view is UIControl { return true }
        if view is UITableViewCell || view is UICollectionViewCell { return true }
        if hasDiscreteTappableGestureRecognizer(view) { return true }
        let traits = view.accessibilityTraits
        if traits.contains(.button) || traits.contains(.link) {
            return true
        }
        return false
    }

    /// Gestures that indicate intentional on-view actions. Excludes `UIPanGestureRecognizer`
    /// so scroll views, maps, and drag surfaces are not logged on every small touch movement.
    private static func hasDiscreteTappableGestureRecognizer(_ view: UIView) -> Bool {
        guard let recognizers = view.gestureRecognizers else { return false }
        for gr in recognizers {
            if gr is UITapGestureRecognizer { return true }
            if gr is UILongPressGestureRecognizer { return true }
            if gr is UISwipeGestureRecognizer { return true }
        }
        return false
    }

    // MARK: - Label Extraction

    /// Priority: UILabel.text → direct UILabel child → accessibilityLabel → recursive text scan.
    /// TextView.text → contentDescription → recursive ViewGroup label scan.
    /// Only runs when captureContext is true.
    internal static func extractLabel(from view: UIView) -> String? {
        // PII safety for text input controls: only use developer-set accessibilityLabel
        // recursive descent — their internal subviews may render placeholder/system text
        // that is NOT developer intent, and .text contains user-entered PII.
        if view is UITextField || view is UITextView || view is UISearchBar {
            let label = view.accessibilityLabel
            return (label?.isEmpty == false) ? label : nil
        }

        // 1. UISegmentedControl: read selected segment title (emitClickEvent fires after dispatch,
        //    so selectedSegmentIndex already reflects the tapped segment)
        if let seg = view as? UISegmentedControl {
            return seg.titleForSegment(at: seg.selectedSegmentIndex)
        }

        // 2. View is itself a UILabel
        if let label = view as? UILabel, let text = label.text, !text.isEmpty {
            return text
        }
        // 2. Single direct UILabel child (e.g. UIButton.titleLabel).
        //    Only applies when there is exactly one — multiple UILabel subviews means this is
        //    a container view (card, cell) and the recursive scan below should collect them all.
        let directLabels = view.subviews.compactMap { $0 as? UILabel }.filter { !($0.text?.isEmpty ?? true) }
        if directLabels.count == 1, let text = directLabels[0].text {
            return text
        }
        // 3. accessibilityLabel
        if let aLabel = view.accessibilityLabel, !aLabel.isEmpty {
            return aLabel
        }
        // 4. Recursive text collection from descendants (container views like cards, cells)
        //    Max depth 4, max 5 segments, joined by " | ", truncated to 200 chars
        let segments = collectTextSegments(from: view, depth: 0)
        guard !segments.isEmpty else { return nil }
        var result = segments.prefix(maxLabelSegments).joined(separator: labelDelimiter)
        if result.count > maxLabelLength {
            var truncated = Array(segments.prefix(maxLabelSegments))
            while truncated.count > 1 {
                truncated.removeLast()
                result = truncated.joined(separator: labelDelimiter)
                if result.count <= maxLabelLength { break }
            }
        }
        return result.isEmpty ? nil : result
    }

    private static func collectTextSegments(from view: UIView, depth: Int) -> [String] {
        guard depth <= maxLabelSearchDepth else { return [] }
        var segments: [String] = []
        for subview in view.subviews {
            // PII safety: never descend into text input views
            if subview is UITextField || subview is UITextView || subview is UISearchBar { continue }
            if let label = subview as? UILabel, let text = label.text, !text.isEmpty {
                segments.append(text)
            } else {
                segments += collectTextSegments(from: subview, depth: depth + 1)
            }
            if segments.count >= maxLabelSegments { break }
        }
        return segments
    }
    static func uninstall() {
        swizzleLock.lock()
        defer { swizzleLock.unlock() }

        buffer?.flush()
        buffer = nil
        emitter = nil
        logger = nil
        touchStartLocations.removeAll()

        if let observer = appLifecycleObserver {
            NotificationCenter.default.removeObserver(observer)
            appLifecycleObserver = nil
        }
    }

}
#endif
