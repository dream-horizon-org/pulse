/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if os(iOS) || os(tvOS)
import UIKit
import ObjectiveC
#endif

internal class UIViewControllerSwizzler {
    private static var trackingSwizzled = false
    private static var lifecycleSwizzled = false
    private static let swizzleLock = NSLock()

    /// Swizzle UIViewController methods selectively.
    /// - `viewDidAppear` and `viewDidDisappear` are always swizzled (screen tracking + app startup).
    /// - `viewDidLoad`, `viewWillAppear`, `viewIsAppearing`, `viewWillDisappear` are only
    ///   swizzled when `includeLifecycleMethods` is true (needed for Created/Restarted/Stopped spans).
    static func swizzle(includeLifecycleMethods: Bool) {
        swizzleLock.lock()
        defer { swizzleLock.unlock() }

        #if os(iOS) || os(tvOS)
        if !trackingSwizzled {
            swizzleViewDidAppear()
            swizzleViewDidDisappear()
            trackingSwizzled = true
        }

        if includeLifecycleMethods && !lifecycleSwizzled {
            swizzleViewDidLoad()
            swizzleViewWillAppear()
            swizzleViewIsAppearing()
            swizzleViewWillDisappear()
            lifecycleSwizzled = true
        }
        #endif
    }

    #if os(iOS) || os(tvOS)

    /// Returns true only for VCs that belong to the main app bundle and are
    /// not container controllers (UINavigationController, UITabBarController,
    /// UISplitViewController). This filters out system VCs and UIKit internals
    /// that would otherwise produce "unknown" or noisy spans.
    private static func shouldTrack(_ viewController: UIViewController) -> Bool {
        guard Bundle(for: type(of: viewController)) == Bundle.main else { return false }
        guard !(viewController is UINavigationController) else { return false }
        guard !(viewController is UITabBarController) else { return false }
        guard !(viewController is UISplitViewController) else { return false }
        return true
    }

    private static func swizzleViewDidLoad() {
        guard let method = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.viewDidLoad)) else {
            return
        }

        var originalIMP: IMP?

        let block: @convention(block) (UIViewController) -> Void = { viewController in
            if shouldTrack(viewController) {
                VisibleScreenTracker.shared.viewControllerDidLoad(viewController)
            }
            if let originalIMP = originalIMP {
                let castedIMP = unsafeBitCast(
                    originalIMP,
                    to: (@convention(c) (UIViewController, Selector) -> Void).self
                )
                castedIMP(viewController, #selector(UIViewController.viewDidLoad))
            }
        }

        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }

    private static func swizzleViewWillAppear() {
        guard let method = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.viewWillAppear(_:))) else {
            return
        }

        var originalIMP: IMP?

        let block: @convention(block) (UIViewController, Bool) -> Void = { viewController, animated in
            if shouldTrack(viewController) {
                VisibleScreenTracker.shared.viewControllerWillAppear(viewController)
            }
            if let originalIMP = originalIMP {
                let castedIMP = unsafeBitCast(
                    originalIMP,
                    to: (@convention(c) (UIViewController, Selector, Bool) -> Void).self
                )
                castedIMP(viewController, #selector(UIViewController.viewWillAppear(_:)), animated)
            }
        }

        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }

    private static func swizzleViewIsAppearing() {
        let selector = NSSelectorFromString("viewIsAppearing:")
        guard let method = class_getInstanceMethod(UIViewController.self, selector) else {
            return
        }

        var originalIMP: IMP?

        let block: @convention(block) (UIViewController, Bool) -> Void = { viewController, animated in
            if shouldTrack(viewController) {
                VisibleScreenTracker.shared.viewControllerIsAppearing(viewController)
            }
            if let originalIMP = originalIMP {
                let castedIMP = unsafeBitCast(
                    originalIMP,
                    to: (@convention(c) (UIViewController, Selector, Bool) -> Void).self
                )
                castedIMP(viewController, selector, animated)
            }
        }

        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }

    private static func swizzleViewDidAppear() {
        guard let method = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.viewDidAppear(_:))) else {
            return
        }
        
        var originalIMP: IMP?
        
        let block: @convention(block) (UIViewController, Bool) -> Void = { viewController, animated in
            // End AppStart for any VC — not gated by shouldTrack so it works
            // in React Native where the host VC isn't in Bundle.main.
            VisibleScreenTracker.shared.endAppStartIfNeeded()
            if shouldTrack(viewController) {
                VisibleScreenTracker.shared.viewControllerDidAppear(viewController)
            }
            if let originalIMP = originalIMP {
                let castedIMP = unsafeBitCast(
                    originalIMP,
                    to: (@convention(c) (UIViewController, Selector, Bool) -> Void).self
                )
                castedIMP(viewController, #selector(UIViewController.viewDidAppear(_:)), animated)
            }
        }
        
        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }

    private static func swizzleViewWillDisappear() {
        guard let method = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.viewWillDisappear(_:))) else {
            return
        }

        var originalIMP: IMP?

        let block: @convention(block) (UIViewController, Bool) -> Void = { viewController, animated in
            if shouldTrack(viewController) {
                VisibleScreenTracker.shared.viewControllerWillDisappear(viewController)
            }
            if let originalIMP = originalIMP {
                let castedIMP = unsafeBitCast(
                    originalIMP,
                    to: (@convention(c) (UIViewController, Selector, Bool) -> Void).self
                )
                castedIMP(viewController, #selector(UIViewController.viewWillDisappear(_:)), animated)
            }
        }

        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }

    private static func swizzleViewDidDisappear() {
        guard let method = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.viewDidDisappear(_:))) else {
            return
        }

        var originalIMP: IMP?

        let block: @convention(block) (UIViewController, Bool) -> Void = { viewController, animated in
            if shouldTrack(viewController) {
                VisibleScreenTracker.shared.viewControllerDidDisappear(viewController)
            }
            if let originalIMP = originalIMP {
                let castedIMP = unsafeBitCast(
                    originalIMP,
                    to: (@convention(c) (UIViewController, Selector, Bool) -> Void).self
                )
                castedIMP(viewController, #selector(UIViewController.viewDidDisappear(_:)), animated)
            }
        }

        let swizzledIMP = imp_implementationWithBlock(unsafeBitCast(block, to: AnyObject.self))
        originalIMP = method_setImplementation(method, swizzledIMP)
    }
    #endif
}