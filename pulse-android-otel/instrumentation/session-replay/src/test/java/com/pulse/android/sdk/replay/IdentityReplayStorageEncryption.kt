package com.pulse.android.sdk.replay

/**
 * No-op encryption for tests: pass-through so persisted payloads can be read as plain JSON.
 */
internal class IdentityReplayStorageEncryption : ReplayStorageEncryption {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
}
