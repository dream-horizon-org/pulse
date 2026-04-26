import Foundation
import CoreGraphics
import PulseKit

// MARK: - Kit

/// `NSNumber` = tri-state: `nil` keep default, `@YES` / `@NO` override. ObjC needs reference types for nested data; for full Swift types use `PulseSDK.initialize` instead.
@objc(PulseObjcKitConfiguration)
public class PulseObjcKitConfiguration: NSObject {
    @objc public var includeScreenAttributes: NSNumber?
    @objc public var includeNetworkAttributes: NSNumber?
    @objc public var includeGlobalAttributes: NSNumber?
}

// MARK: - Instrumentation blocks

@objc(PulseObjcEnabledConfig)
public class PulseObjcEnabledConfig: NSObject {
    @objc public var enabled: NSNumber?

    @objc public static func enabled() -> PulseObjcEnabledConfig {
        let c = PulseObjcEnabledConfig(); c.enabled = true; return c
    }
    @objc public static func disabled() -> PulseObjcEnabledConfig {
        let c = PulseObjcEnabledConfig(); c.enabled = false; return c
    }
}

@objc(PulseObjcSessionsConfig)
public class PulseObjcSessionsConfig: NSObject {
    @objc public var enabled: NSNumber?
    @objc public var maxLifetimeSeconds: NSNumber?
    @objc public var backgroundInactivityTimeoutSeconds: NSNumber?
    @objc public var shouldPersist: NSNumber?

    @objc public static func enabled() -> PulseObjcSessionsConfig {
        let c = PulseObjcSessionsConfig(); c.enabled = true; return c
    }
    @objc public static func disabled() -> PulseObjcSessionsConfig {
        let c = PulseObjcSessionsConfig(); c.enabled = false; return c
    }
}

@objc(PulseObjcRageConfig)
public class PulseObjcRageConfig: NSObject {
    @objc public var timeWindowMs: NSNumber?
    @objc public var rageThreshold: NSNumber?
    @objc public var radiusPt: NSNumber?
}

@objc(PulseObjcUIKitTapConfig)
public class PulseObjcUIKitTapConfig: NSObject {
    @objc public var enabled: NSNumber?
    @objc public var captureContext: NSNumber?
    @objc public var rage: PulseObjcRageConfig?
}

@objc(PulseObjcSessionReplayConfig)
public class PulseObjcSessionReplayConfig: NSObject {
    @objc public var enabled: NSNumber?
    @objc public var replayEndpointBaseUrl: String?
    @objc public var textAndInputPrivacy: String?
    @objc public var imagePrivacy: String?
    @objc public var captureIntervalMs: NSNumber?
    @objc public var compressionQuality: NSNumber?
    @objc public var screenshotScale: NSNumber?
    @objc public var flushIntervalSeconds: NSNumber?
    @objc public var flushAt: NSNumber?
    @objc public var maxBatchSize: NSNumber?
    @objc public var maskViewClasses: NSArray?
    @objc public var unmaskViewClasses: NSArray?
}

@objc(PulseObjcInstrumentations)
public class PulseObjcInstrumentations: NSObject {
    @objc public var urlSession: PulseObjcEnabledConfig?
    @objc public var sessions: PulseObjcSessionsConfig?
    @objc public var signPost: PulseObjcEnabledConfig?
    @objc public var interaction: PulseObjcEnabledConfig?
    @objc public var location: PulseObjcEnabledConfig?
    @objc public var crash: PulseObjcEnabledConfig?
    @objc public var appLifecycle: PulseObjcEnabledConfig?
    @objc public var screenLifecycle: PulseObjcEnabledConfig?
    @objc public var appStartup: PulseObjcEnabledConfig?
    @objc public var uiKitTap: PulseObjcUIKitTapConfig?
    @objc public var sessionReplay: PulseObjcSessionReplayConfig?
}

// MARK: - Mappers (internal)

enum PulseObjcInitMappers {
    static func makeConfiguration(
        from configuration: PulseObjcKitConfiguration?
    ) -> ((inout PulseKitConfiguration) -> Void)? {
        guard let configuration else { return nil }
        let incScreen = boolNumber(configuration.includeScreenAttributes)
        let incNet = boolNumber(configuration.includeNetworkAttributes)
        let incGlob = boolNumber(configuration.includeGlobalAttributes)
        guard incScreen != nil || incNet != nil || incGlob != nil else { return nil }
        return { out in
            if let v = incScreen { out.includeScreenAttributes = v }
            if let v = incNet { out.includeNetworkAttributes = v }
            if let v = incGlob { out.includeGlobalAttributes = v }
        }
    }

    static func makeInstrumentations(
        from root: PulseObjcInstrumentations?
    ) -> ((inout InstrumentationConfiguration) -> Void)? {
        guard let root else { return nil }
        return { config in
            if let b = boolNumber(root.urlSession?.enabled) {
                config.urlSession { $0.enabled(b) }
            }
            if let s = root.sessions,
               s.enabled != nil
                || s.maxLifetimeSeconds != nil
                || s.backgroundInactivityTimeoutSeconds != nil
                || s.shouldPersist != nil {
                config.sessions { sess in
                    if let v = boolNumber(s.enabled) { sess.enabled(v) }
                    if let v = s.maxLifetimeSeconds?.doubleValue { sess.maxLifetime(v) }
                    if let v = s.backgroundInactivityTimeoutSeconds?.doubleValue {
                        sess.backgroundInactivityTimeout(v)
                    }
                    if let v = boolNumber(s.shouldPersist) { sess.shouldPersist(v) }
                }
            }
            if let b = boolNumber(root.signPost?.enabled) {
                config.signPost { $0.enabled(b) }
            }
            if let b = boolNumber(root.interaction?.enabled) {
                config.interaction { $0.enabled(b) }
            }
            if let b = boolNumber(root.location?.enabled) {
                config.location { $0.enabled(b) }
            }
            if let b = boolNumber(root.crash?.enabled) {
                config.crash { $0.enabled(b) }
            }
            if let b = boolNumber(root.appLifecycle?.enabled) {
                config.appLifecycle { $0.enabled(b) }
            }
            if let b = boolNumber(root.screenLifecycle?.enabled) {
                config.screenLifecycle { $0.enabled(b) }
            }
            if let b = boolNumber(root.appStartup?.enabled) {
                config.appStartup { $0.enabled(b) }
            }
            if let t = root.uiKitTap {
                let has = t.enabled != nil || t.captureContext != nil || t.rage != nil
                if has {
                    config.uiKitTap { tap in
                        if let v = boolNumber(t.enabled) { tap.enabled(v) }
                        if let v = boolNumber(t.captureContext) { tap.captureContext(v) }
                        if let rage = t.rage {
                            tap.rage { r in
                                if let v = rage.timeWindowMs?.intValue { r.timeWindowMs = v }
                                if let v = rage.rageThreshold?.intValue { r.rageThreshold = v }
                                if let v = rage.radiusPt?.floatValue { r.radiusPt = v }
                            }
                        }
                    }
                }
            }
            if let sr = root.sessionReplay {
                let nestedConfig =
                    sr.replayEndpointBaseUrl != nil
                    || sr.textAndInputPrivacy != nil
                    || sr.imagePrivacy != nil
                    || sr.captureIntervalMs != nil
                    || sr.compressionQuality != nil
                    || sr.screenshotScale != nil
                    || sr.flushIntervalSeconds != nil
                    || sr.flushAt != nil
                    || sr.maxBatchSize != nil
                    || sr.maskViewClasses != nil
                    || sr.unmaskViewClasses != nil
                let doReplay = sr.enabled != nil || nestedConfig
                if doReplay {
                    config.sessionReplay { replay in
                        if let v = boolNumber(sr.enabled) { replay.enabled(v) }
                        if nestedConfig {
                            replay.configure { local in
                                if let s = sr.replayEndpointBaseUrl { local.replayEndpointBaseUrl = s }
                                if let s = sr.textAndInputPrivacy {
                                    local.textAndInputPrivacy = textPrivacy(from: s)
                                }
                                if let s = sr.imagePrivacy { local.imagePrivacy = imagePrivacy(from: s) }
                                if let v = sr.captureIntervalMs?.intValue { local.captureIntervalMs = v }
                                if let v = sr.compressionQuality?.doubleValue {
                                    local.compressionQuality = CGFloat(v)
                                }
                                if let v = sr.screenshotScale?.doubleValue { local.screenshotScale = CGFloat(v) }
                                if let v = sr.flushIntervalSeconds?.doubleValue { local.flushIntervalSeconds = v }
                                if let v = sr.flushAt?.intValue { local.flushAt = v }
                                if let v = sr.maxBatchSize?.intValue { local.maxBatchSize = v }
                                if let arr = sr.maskViewClasses { local.maskViewClasses = stringSet(from: arr) }
                                if let arr = sr.unmaskViewClasses { local.unmaskViewClasses = stringSet(from: arr) }
                            }
                        }
                    }
                }
            }
        }
    }

    private static func boolNumber(_ n: NSNumber?) -> Bool? {
        n.map { $0.boolValue }
    }

    private static func stringSet(from: NSArray) -> Set<String> {
        var out = Set<String>()
        for o in from {
            if let s = o as? String { out.insert(s) }
            else if let s = o as? NSString { out.insert(s as String) }
        }
        return out
    }

    private static func textPrivacy(from s: String) -> TextAndInputPrivacy {
        switch s.lowercased() {
        case "maskallinputs", "mask_all_inputs":
            return .maskAllInputs
        case "masksensitiveinputs", "mask_sensitive_inputs":
            return .maskSensitiveInputs
        default:
            return .maskAll
        }
    }

    private static func imagePrivacy(from s: String) -> ImagePrivacy {
        if s.lowercased() == "masknone" || s.lowercased() == "mask_none" {
            return .maskNone
        }
        return .maskAll
    }
}
