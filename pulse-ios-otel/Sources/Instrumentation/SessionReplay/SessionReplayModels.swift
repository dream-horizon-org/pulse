/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if os(iOS) || os(tvOS)
import UIKit
import ObjectiveC
#if canImport(SwiftUI)
import SwiftUI
#endif
#endif

public enum TextAndInputPrivacy {
    /// Mask ALL text content — static labels, input fields, hints. Most restrictive (default).
    case maskAll
    /// Mask only user-editable inputs (UITextField, UITextView). Static UILabel content is shown.
    case maskAllInputs
    /// Mask only sensitive input types: password, email, phone. All other text and inputs are shown.
    case maskSensitiveInputs
}

public enum ImagePrivacy {
    /// Replace all images with a solid black mask rectangle (default).
    case maskAll
    /// Show all images without masking.
    case maskNone
}

public struct SessionReplayConfig {
    public var captureIntervalMs: Int = 1000
    public var compressionQuality: CGFloat = 0.3
    public var textAndInputPrivacy: TextAndInputPrivacy = .maskAll
    public var imagePrivacy: ImagePrivacy = .maskAll
    @available(*, deprecated, message: "Use textAndInputPrivacy instead")
    public var maskAllTextInputs: Bool {
        get {
            switch textAndInputPrivacy {
            case .maskAll: return true
            case .maskAllInputs, .maskSensitiveInputs: return false
            }
        }
        set {
            textAndInputPrivacy = newValue ? .maskAll : .maskSensitiveInputs
        }
    }
    @available(*, deprecated, message: "Use imagePrivacy instead")
    public var maskAllImages: Bool {
        get {
            return imagePrivacy == .maskAll
        }
        set {
            imagePrivacy = newValue ? .maskAll : .maskNone
        }
    }
    public var screenshotScale: CGFloat = 1.0
    public var flushIntervalSeconds: TimeInterval = 60
    public var flushAt: Int = 10
    public var maxBatchSize: Int = 50
    public var replayEndpointBaseUrl: String?
    
    /// Set of view class names (fully-qualified) that should always be masked.
    /// Applies to the registered class and all its subclasses.
    public var maskViewClasses: Set<String> = []
    
    /// Set of view class names (fully-qualified) that should always be unmasked.
    /// Applies to the registered class and all its subclasses.
    public var unmaskViewClasses: Set<String> = []
    
    // MARK: - Effective (Clamped) Properties
    
    /// Capture interval clamped to minimum 100ms to prevent excessive frame capture.
    public var effectiveCaptureIntervalMs: Int {
        max(100, captureIntervalMs)
    }
    
    /// Compression quality clamped to 0.0–1.0 range for valid UIGraphicsImageRenderer output.
    public var effectiveCompressionQuality: CGFloat {
        max(0.0, min(1.0, compressionQuality))
    }
    
    /// Screenshot scale clamped to 0.01–1.0 range for valid capture dimensions.
    public var effectiveScreenshotScale: CGFloat {
        max(0.01, min(1.0, screenshotScale))
    }
    
    /// Flush interval clamped to minimum 1.0 second to prevent excessive flushing.
    public var effectiveFlushIntervalSeconds: TimeInterval {
        max(1.0, flushIntervalSeconds)
    }
    
    /// Flush threshold clamped to minimum 1 to ensure at least one payload per flush.
    public var effectiveFlushAt: Int {
        max(1, flushAt)
    }
    
    /// Batch size clamped to minimum 1 to ensure non-empty batches.
    public var effectiveMaxBatchSize: Int {
        max(1, maxBatchSize)
    }

    public init(
        captureIntervalMs: Int = 1000,
        compressionQuality: CGFloat = 0.3,
        textAndInputPrivacy: TextAndInputPrivacy = .maskAll,
        imagePrivacy: ImagePrivacy = .maskAll,
        screenshotScale: CGFloat = 1.0,
        flushIntervalSeconds: TimeInterval = 60,
        flushAt: Int = 10,
        maxBatchSize: Int = 50,
        replayEndpointBaseUrl: String? = nil,
        maskViewClasses: Set<String> = [],
        unmaskViewClasses: Set<String> = []
    ) {
        self.captureIntervalMs = captureIntervalMs
        self.compressionQuality = compressionQuality
        self.textAndInputPrivacy = textAndInputPrivacy
        self.imagePrivacy = imagePrivacy
        self.screenshotScale = screenshotScale
        self.flushIntervalSeconds = flushIntervalSeconds
        self.flushAt = flushAt
        self.maxBatchSize = maxBatchSize
        self.replayEndpointBaseUrl = replayEndpointBaseUrl
        self.maskViewClasses = maskViewClasses
        self.unmaskViewClasses = unmaskViewClasses
    }
    
    @available(*, deprecated, message: "Use init with textAndInputPrivacy and imagePrivacy instead")
    public init(
        captureIntervalMs: Int = 1000,
        compressionQuality: CGFloat = 0.3,
        maskAllTextInputs: Bool = true,
        maskAllImages: Bool = true,
        screenshotScale: CGFloat = 1.0,
        flushIntervalSeconds: TimeInterval = 60,
        flushAt: Int = 10,
        maxBatchSize: Int = 50,
        replayEndpointBaseUrl: String? = nil
    ) {
        self.captureIntervalMs = captureIntervalMs
        self.compressionQuality = compressionQuality
        self.textAndInputPrivacy = maskAllTextInputs ? .maskAll : .maskSensitiveInputs
        self.imagePrivacy = maskAllImages ? .maskAll : .maskNone
        self.screenshotScale = screenshotScale
        self.flushIntervalSeconds = flushIntervalSeconds
        self.flushAt = flushAt
        self.maxBatchSize = maxBatchSize
        self.replayEndpointBaseUrl = replayEndpointBaseUrl
        self.maskViewClasses = []
        self.unmaskViewClasses = []
    }
    
    public mutating func addMaskViewClass(_ className: String) {
        maskViewClasses.insert(className)
    }
    
    public mutating func addUnmaskViewClass(_ className: String) {
        unmaskViewClasses.insert(className)
    }
}

public struct SessionReplayFrame {
    public enum ImageFormat: String {
        case webp
        case jpeg
    }

    public let timestamp: Date
    public let sessionId: String
    public let screenName: String
    public let imageData: Data
    public let format: ImageFormat
    public let width: Int
    public let height: Int

    public init(
        timestamp: Date,
        sessionId: String,
        screenName: String,
        imageData: Data,
        format: ImageFormat,
        width: Int,
        height: Int
    ) {
        self.timestamp = timestamp
        self.sessionId = sessionId
        self.screenName = screenName
        self.imageData = imageData
        self.format = format
        self.width = width
        self.height = height
    }
}

#if os(iOS) || os(tvOS)
extension UIView {
    private static let pulseReplayMaskTagValue = "pulse-mask"
    private static let pulseReplayUnmaskTagValue = "pulse-unmask"
    private static let pulseReplayPrivacyTagKey = 999998
    
    public func pulseReplayMask() {
        self.setTag(Self.pulseReplayPrivacyTagKey, Self.pulseReplayMaskTagValue)
    }
    
    public func pulseReplayUnmask() {
        self.setTag(Self.pulseReplayPrivacyTagKey, Self.pulseReplayUnmaskTagValue)
    }
    
    internal func getPrivacyTagValue() -> String? {
        self.getTag(Self.pulseReplayPrivacyTagKey) as? String
    }
    
    internal var hasPulseReplayMaskTag: Bool {
        getPrivacyTagValue() == Self.pulseReplayMaskTagValue
    }
    
    internal var hasPulseReplayUnmaskTag: Bool {
        getPrivacyTagValue() == Self.pulseReplayUnmaskTagValue
    }
    
    internal var hasInstanceMaskOverride: Bool? {
        if hasPulseReplayUnmaskTag {
            return false
        }
        if hasPulseReplayMaskTag {
            return true
        }
        
        let accessibilityLabel = self.accessibilityLabel?.lowercased() ?? ""
        if accessibilityLabel.contains(Self.pulseReplayUnmaskTagValue) {
            return false
        }
        if accessibilityLabel.contains(Self.pulseReplayMaskTagValue) {
            return true
        }
        
        if let accessibilityId = self.accessibilityIdentifier {
            let lowerId = accessibilityId.lowercased()
            if lowerId.contains(Self.pulseReplayUnmaskTagValue) {
                return false
            }
            if lowerId.contains(Self.pulseReplayMaskTagValue) {
                return true
            }
        }
        
        return nil
    }
    
    private func setTag(_ key: Int, _ value: String) {
        let keyPointer = UnsafeRawPointer(bitPattern: key)!
        objc_setAssociatedObject(self, keyPointer, value, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    }
    
    private func getTag(_ key: Int) -> Any? {
        let keyPointer = UnsafeRawPointer(bitPattern: key)!
        return objc_getAssociatedObject(self, keyPointer)
    }
}

#if canImport(SwiftUI)
@available(iOS 14.0, macOS 11.0, tvOS 14.0, watchOS 7.0, *)
public struct PulseReplayMaskModifier: ViewModifier {
    private let shouldMask: Bool
    
    public init(shouldMask: Bool = true) {
        self.shouldMask = shouldMask
    }
    
    public func body(content: Content) -> some View {
        content
            .accessibilityIdentifier(shouldMask ? "pulse-replay-mask" : "pulse-replay-unmask")
    }
}

@available(iOS 14.0, macOS 11.0, tvOS 14.0, watchOS 7.0, *)
extension View {
    public func pulseReplayMask() -> some View {
        modifier(PulseReplayMaskModifier(shouldMask: true))
    }
    
    public func pulseReplayUnmask() -> some View {
        modifier(PulseReplayMaskModifier(shouldMask: false))
    }
    
    @available(*, deprecated, message: "Use pulseReplayMask() or pulseReplayUnmask() instead")
    public func pulseReplayMask(isEnabled: Bool) -> some View {
        modifier(PulseReplayMaskModifier(shouldMask: isEnabled))
    }
}
#endif
#endif
