package org.dreamhorizon.pulseserver.util.encryption;

/**
 * Interface for encryption operations.
 * Implementations provide AES-GCM encryption with SHA-256 digest for verification.
 */
public interface EncryptionUtil {

  /**
   * Encrypts a plain text value using AES-GCM.
   *
   * @param plainValue the value to encrypt
   * @return EncryptedData containing encrypted value, salt, and digest
   */
  EncryptedData encrypt(String plainValue);

  /**
   * Decrypts an encrypted value.
   *
   * @param encryptedValue the Base64-encoded encrypted value (includes IV)
   * @return the original plain text
   */
  String decrypt(String encryptedValue);

  /**
   * Generates a SHA-256 digest of the input.
   *
   * @param input the input to hash
   * @return Base64-encoded digest
   */
  String generateDigest(String input);

  /**
   * Verifies a plain value against a stored digest.
   *
   * @param plainValue the plain value to verify
   * @param salt the salt used during encryption
   * @param storedDigest the stored digest to compare against
   * @return true if verification succeeds, false otherwise
   */
  boolean verify(String plainValue, String salt, String storedDigest);
}

