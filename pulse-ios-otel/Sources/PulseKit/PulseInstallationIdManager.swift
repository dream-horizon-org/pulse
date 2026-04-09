/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Manages the installation ID for the app instance.
/// The installation ID remains constant for the entire app installation
/// and only gets reset when the user uninstalls the app.
internal class PulseInstallationIdManager {
    
    private static let installationIdKey = "pulse_installation_id"
    
    private let userDefaults: UserDefaults
    private let loggerProvider: () -> Logger
    private let lock = NSLock()
    
    private var _installationId: String?
    private var hasEmittedEvent: Bool = false
    
    /// Gets the installation ID, generating a new one if it doesn't exist.
    /// This ID persists for the entire app installation and is only reset on uninstall.
    var installationId: String {
        lock.lock()
        defer { lock.unlock() }
        
        if let cachedId = _installationId {
            return cachedId
        }
        
        // Try to get from UserDefaults
        if let storedId = userDefaults.string(forKey: PulseInstallationIdManager.installationIdKey) {
            _installationId = storedId
            return storedId
        }
        
        // Generate new ID
        let newId = generateAndStoreInstallationId()
        _installationId = newId
        return newId
    }
    
    init(userDefaults: UserDefaults = UserDefaults.standard, loggerProvider: @escaping () -> Logger) {
        self.userDefaults = userDefaults
        self.loggerProvider = loggerProvider
    }
    
    private func generateAndStoreInstallationId() -> String {
        let newId = UUID().uuidString
        
        // Store in UserDefaults (persists until app uninstall)
        userDefaults.set(newId, forKey: PulseInstallationIdManager.installationIdKey)
        
        // Emit app installation start event (deferred to main queue to ensure OTel is initialized)
        DispatchQueue.main.async { [weak self] in
            self?.emitInstallationStartEvent(installationId: newId)
        }
        
        return newId
    }
    
    private func emitInstallationStartEvent(installationId: String) {
        guard !hasEmittedEvent else { return }
        hasEmittedEvent = true
        
        let logger = loggerProvider()
        let attributes: [String: AttributeValue] = [
            PulseAttributes.appInstallationId: AttributeValue.string(installationId),
            PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.PulseTypeValues.appInstallationStart)
        ]
        
        logger.logRecordBuilder()
            .setAttributes(attributes)
            .setEventName(PulseAttributes.PulseTypeValues.appInstallationStart)
            .emit()
    }
}

