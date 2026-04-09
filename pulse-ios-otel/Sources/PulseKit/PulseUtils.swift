/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

#if os(iOS) || os(tvOS)
import UIKit
#endif

/// Utility functions for computing UI viewport metrics.
internal class PulseUtils {
    /// Cache for computed GCD values using string keys.
    private static var gcdCache: [String: Int] = [:]
    private static let gcdCacheLock = NSLock()
    
    /// Computes current viewport aspect ratio from active key UIWindow as "w:h".
    /// Uses window bounds in points to match click viewport width/height semantics.
    static func currentViewportAspectRatio() -> String? {
        #if os(iOS) || os(tvOS)
        var aspectRatio: String?
        DispatchQueue.main.async {
            aspectRatio = currentViewportAspectRatioOnMainThread()
        }
        return aspectRatio
        #else
        return nil
        #endif
    }

    #if os(iOS) || os(tvOS)
    private static func currentViewportAspectRatioOnMainThread() -> String? {
        guard
            let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive }),
            let window = scene.windows.first(where: { $0.isKeyWindow })
        else {
            return nil
        }

        let width = Int(window.bounds.width)
        let height = Int(window.bounds.height)
        guard width > 0, height > 0 else { return nil }

        let divisor = gcd(width, height)
        return "\(width / divisor):\(height / divisor)"
    }
    #endif

    /// Computes the greatest common divisor using Euclidean algorithm with memoization.
    private static func gcd(_ a: Int, _ b: Int) -> Int {
        let x = abs(max(a, b))
        let y = abs(min(a, b))
        let cacheKey = "\(x),\(y)"
        
        if let cached = cachedGCDValue(for: cacheKey) {
            return cached
        }

        let result = (y == 0) ? x : gcd(y, x % y)

        cacheGCDValue(result, for: cacheKey)

        return result
    }

    private static func cachedGCDValue(for key: String) -> Int? {
        gcdCacheLock.lock()
        defer { gcdCacheLock.unlock() }
        return gcdCache[key]
    }

    private static func cacheGCDValue(_ value: Int, for key: String) {
        gcdCacheLock.lock()
        gcdCache[key] = value
        gcdCacheLock.unlock()
    }
}
