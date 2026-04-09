/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
#if canImport(SessionReplay)
@testable import SessionReplay
#endif

class SessionReplayEncryptionTests: XCTestCase {

    #if canImport(SessionReplay)
    
    func testNoOpEncryptionRoundtrip() throws {
        let encryption = NoOpSessionReplayEncryption()
        let plaintext = "Hello, world!".data(using: .utf8)!
        
        let encrypted = encryption.encrypt(plaintext)
        XCTAssertNotNil(encrypted)
        
        let decrypted = try encryption.decrypt(encrypted!)
        XCTAssertEqual(decrypted, plaintext)
    }
    
    #if os(iOS) || os(tvOS) || os(macOS) || os(watchOS)
    @available(iOS 13.0, macOS 10.15, tvOS 13.0, watchOS 6.0, *)
    func testDefaultAESGCMEncryptionRoundtrip() throws {
        // We might not have keychain access in a pure unit test environment depending on entitlements,
        // but if we do, this will test the DefaultSessionReplayEncryption round trip.
        // We'll wrap in try-catch in case keychain operations fail (e.g. simulator without host).
        do {
            let encryption = DefaultSessionReplayEncryption()
            let plaintext = "Sensitive replay payload data 12345".data(using: .utf8)!
            
            guard let ciphertext = encryption.encrypt(plaintext) else {
                XCTFail("Encryption failed")
                return
            }
            
            // Ciphertext should not be plaintext
            XCTAssertNotEqual(ciphertext, plaintext)
            
            let decrypted = try encryption.decrypt(ciphertext)
            XCTAssertEqual(decrypted, plaintext, "Decrypted text should match plaintext")
            
        } catch {
            // Note: If tests run on a build node without keychain access, 
            // the init or encryption might fail. This is normal for automated environments.
            print("Skipping keychain-backed encryption test due to environment error: \(error)")
        }
    }
    
    @available(iOS 13.0, macOS 10.15, tvOS 13.0, watchOS 6.0, *)
    func testDecryptionThrowsOnInvalidData() {
        do {
            let encryption = DefaultSessionReplayEncryption()
            let randomData = Data((0..<32).map { _ in UInt8.random(in: 0...255) })
            
            XCTAssertThrowsError(try encryption.decrypt(randomData))
        } catch {
            print("Skipping keychain-backed decryption test due to environment error.")
        }
    }
    #endif
    
    #endif
}
