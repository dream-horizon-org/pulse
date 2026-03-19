package com.pulse.android.sdk.replay

/**
 * Encryption for replay batches persisted to disk. The SDK uses a default implementation (AES-256-GCM)
 * internally; this interface is not exposed via configuration.
 */
public interface ReplayStorageEncryption {
    /**
     * Encrypt the payload (e.g. envelope JSON bytes) before writing to file.
     * @param plaintext UTF-8 bytes of the envelope JSON
     * @return encrypted bytes to write to disk
     */
    public fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Decrypt the payload read from file.
     * @param ciphertext bytes read from the replay file
     * @return decrypted UTF-8 bytes (envelope JSON)
     * @throws Exception if decryption fails (e.g. wrong key, corrupted data, or file was stored unencrypted)
     */
    public fun decrypt(ciphertext: ByteArray): ByteArray
}
