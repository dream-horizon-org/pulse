import Foundation
import PulseKit

/**
 * Allows React Native to override iOS ViewController-based screen tracking.
 */
@objc(ReactNativeScreenNameTracker)
public class ReactNativeScreenNameTracker: NSObject {
    private static let lock = NSLock()
    private static var _currentScreenName: String?
    private static var _previousScreenName: String?
    
    @objc public static func setCurrentScreenName(_ screenName: String?) {
        let changed: Bool
        lock.lock()
        if _currentScreenName != screenName {
            if let current = _currentScreenName {
                _previousScreenName = current
            }
            _currentScreenName = screenName
            changed = true
        } else {
            changed = false
        }
        lock.unlock()

        if changed {
            SessionReplayInstrumentation.getInstance()?.recorderInstance?.notifyScreenChange()
        }
    }
    
    static func getCurrentScreenName() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return _currentScreenName
    }

    static func getPreviousScreenName() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return _previousScreenName
    }
}

