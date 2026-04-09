/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk

/// Manages user ID and user properties with persistence.
/// 
/// - Stores userId in UserDefaults for persistence across app launches
/// - Emits pulse.user.session.start and pulse.user.session.end log events when userId changes
/// - User properties are stored in-memory only (not persisted)
internal class PulseUserSessionEmitter {
    private let loggerProvider: () -> Logger
    private let userDefaults: UserDefaults
    private let userPropertiesQueue: DispatchQueue
    
    private static let userIdKey = "user_id"
    
    private var _userId: String?
    private var isUserIdFetched: Bool = false
    
    // In-memory user properties
    private var _userProperties: [String: AttributeValue] = [:]
    
    var userId: String? {
        get {
            return userPropertiesQueue.sync {
                if isUserIdFetched {
                    return _userId
                } else {
                    // Load from UserDefaults
                    _userId = userDefaults.string(forKey: PulseUserSessionEmitter.userIdKey)
                    isUserIdFetched = true
                    return _userId
                }
            }
        }
        set {
            var oldUserId: String?
            var shouldUpdate = false
            
            // Capture old userId and check if update is needed within sync block
            userPropertiesQueue.sync {
                oldUserId = isUserIdFetched ? _userId : userDefaults.string(forKey: PulseUserSessionEmitter.userIdKey)
                shouldUpdate = newValue != oldUserId
                
                if !shouldUpdate {
                    return
                }
                
                // Save to UserDefaults
                if let newValue = newValue {
                    userDefaults.set(newValue, forKey: PulseUserSessionEmitter.userIdKey)
                } else {
                    userDefaults.removeObject(forKey: PulseUserSessionEmitter.userIdKey)
                }
                
                isUserIdFetched = true
                _userId = newValue
            }
            
            // Emit session events outside sync block to avoid potential deadlocks
            if shouldUpdate {
                updateUserId(newUserId: newValue, oldUserId: oldUserId)
            }
        }
    }
    
    var userProperties: [String: AttributeValue] {
        get {
            userPropertiesQueue.sync {
                return _userProperties
            }
        }
    }
    
    init(loggerProvider: @escaping () -> Logger, userDefaults: UserDefaults = UserDefaults.standard) {
        self.loggerProvider = loggerProvider
        self.userDefaults = userDefaults
        self.userPropertiesQueue = DispatchQueue(label: "com.pulse.ios.sdk.userProperties")
    }
    
    /// Set user property
    func setUserProperty(name: String, value: AttributeValue?) {
        userPropertiesQueue.sync {
            if let value = value {
                _userProperties[name] = value
            } else {
                _userProperties.removeValue(forKey: name)
            }
        }
    }
    
    /// Set multiple user properties
    func setUserProperties(_ properties: [String: AttributeValue?]) {
        userPropertiesQueue.sync {
            for (key, value) in properties {
                if let value = value {
                    _userProperties[key] = value
                } else {
                    _userProperties.removeValue(forKey: key)
                }
            }
        }
    }
    
    private func updateUserId(newUserId: String?, oldUserId: String?) {
        let logger = loggerProvider()
        
        // Emit session.end event if old userId existed
        if let oldUserId = oldUserId {
            let attributes: [String: AttributeValue] = [
                PulseAttributes.userId: AttributeValue.string(oldUserId),
                PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.pulseUserSessionEndEventName)
            ]
            logger.logRecordBuilder()
                .setAttributes(attributes)
                .setEventName(PulseAttributes.pulseUserSessionEndEventName)
                .emit()
        }
        
        // Emit session.start event if new userId exists
        if let newUserId = newUserId {
            var attributes: [String: AttributeValue] = [
                PulseAttributes.userId: AttributeValue.string(newUserId),
                PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.pulseUserSessionStartEventName)
            ]
            
            // Add previous user ID if it existed
            if let oldUserId = oldUserId {
                attributes[PulseAttributes.pulseUserPreviousId] = AttributeValue.string(oldUserId)
            }
            
            logger.logRecordBuilder()
                .setAttributes(attributes)
                .setEventName(PulseAttributes.pulseUserSessionStartEventName)
                .emit()
        }
    }
}

