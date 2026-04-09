/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import CryptoKit
import Security

internal protocol SessionReplayStorageEncryption {
    func encrypt(_ plaintext: Data) -> Data?
    func decrypt(_ ciphertext: Data) throws -> Data
}

@available(iOS 13.0, macOS 10.15, tvOS 13.0, watchOS 6.0, *)
internal final class DefaultSessionReplayEncryption: SessionReplayStorageEncryption {

    private static let keychainService = "com.pulse.ios.sdk.sessionreplay"
    private static let keychainAccount = "replay_encryption_key"

    private let key: SymmetricKey

    init() {
        self.key = Self.getOrCreateKey()
    }

    func encrypt(_ plaintext: Data) -> Data? {
        do {
            let sealedBox = try AES.GCM.seal(plaintext, using: key)
            guard let combined = sealedBox.combined else { return nil }
            return combined
        } catch {
            return nil
        }
    }

    func decrypt(_ ciphertext: Data) throws -> Data {
        let sealedBox = try AES.GCM.SealedBox(combined: ciphertext)
        return try AES.GCM.open(sealedBox, using: key)
    }

    private static func getOrCreateKey() -> SymmetricKey {
        if let existingKeyData = loadKeyFromKeychain() {
            return SymmetricKey(data: existingKeyData)
        }

        let newKey = SymmetricKey(size: .bits256)
        let keyData = newKey.withUnsafeBytes { Data($0) }
        saveKeyToKeychain(keyData)
        return newKey
    }

    private static func loadKeyFromKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecSuccess, let data = result as? Data {
            return data
        }
        return nil
    }

    private static func saveKeyToKeychain(_ keyData: Data) {
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecValueData as String: keyData,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        SecItemAdd(addQuery as CFDictionary, nil)
    }
}

internal final class NoOpSessionReplayEncryption: SessionReplayStorageEncryption {
    func encrypt(_ plaintext: Data) -> Data? {
        plaintext
    }

    func decrypt(_ ciphertext: Data) throws -> Data {
        ciphertext
    }
}
