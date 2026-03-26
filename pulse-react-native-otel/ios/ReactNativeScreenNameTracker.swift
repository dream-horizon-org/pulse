import Foundation

/**
 * Allows React Native to override iOS ViewController-based screen tracking.
 */
@objc(ReactNativeScreenNameTracker)
public class ReactNativeScreenNameTracker: NSObject {
    private static let lock = NSLock()
    private static var _currentScreenName: String?
    private static var _previousScreenName: String?
    
    @objc public static func setCurrentScreenName(_ screenName: String?) {
        lock.lock()
        defer { lock.unlock() }
        if let current = _currentScreenName, current != screenName {
            _previousScreenName = current
        }
        _currentScreenName = screenName
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

