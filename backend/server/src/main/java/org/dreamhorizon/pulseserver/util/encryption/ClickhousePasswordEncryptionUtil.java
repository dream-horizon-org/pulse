package org.dreamhorizon.pulseserver.util.encryption;

import static org.dreamhorizon.pulseserver.util.encryption.EncryptionConstants.ALGORITHM;
import static org.dreamhorizon.pulseserver.util.encryption.EncryptionConstants.DIGEST_ALGORITHM;
import static org.dreamhorizon.pulseserver.util.encryption.EncryptionConstants.GCM_IV_LENGTH;
import static org.dreamhorizon.pulseserver.util.encryption.EncryptionConstants.GCM_TAG_LENGTH;
import static org.dreamhorizon.pulseserver.util.encryption.EncryptionConstants.SALT_LENGTH;
import static org.dreamhorizon.pulseserver.util.encryption.EncryptionConstants.TRANSFORMATION;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;

/**
 * Encryption utility for ClickHouse tenant passwords.
 * Uses AES-GCM encryption with SHA-256 digest for verification.
 */
@Slf4j
@Singleton
public class ClickhousePasswordEncryptionUtil implements EncryptionUtil {

  private final SecretKey secretKey;
  private final SecureRandom secureRandom;

  @Inject
  public ClickhousePasswordEncryptionUtil(ApplicationConfig applicationConfig) {
    try {
      String encryptionKey = applicationConfig.getEncryptionMasterKey();
      if (encryptionKey == null || encryptionKey.isBlank()) {
        throw new IllegalStateException("encryptionMasterKey is not configured in ApplicationConfig");
      }
      byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
      this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
      this.secureRandom = new SecureRandom();
      log.info("ClickhousePasswordEncryptionUtil initialized successfully (AES-GCM)");
    } catch (Exception e) {
      log.error("Failed to initialize ClickhousePasswordEncryptionUtil", e);
      throw new RuntimeException("Encryption key initialization failed", e);
    }
  }

  @Override
  public EncryptedData encrypt(String plainValue) {
    try {
      // Generate random salt for digest
      byte[] salt = new byte[SALT_LENGTH];
      secureRandom.nextBytes(salt);
      String saltBase64 = Base64.getEncoder().encodeToString(salt);

      // Generate random IV for GCM
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      // Encrypt using AES-GCM
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
      byte[] encryptedBytes = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));

      // Combine IV + encrypted data for storage
      ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
      byteBuffer.put(iv);
      byteBuffer.put(encryptedBytes);
      String encryptedBase64 = Base64.getEncoder().encodeToString(byteBuffer.array());

      String digest = generateDigest(plainValue + saltBase64);

      return EncryptedData.builder()
          .encryptedValue(encryptedBase64)
          .salt(saltBase64)
          .digest(digest)
          .build();
    } catch (Exception e) {
      log.error("Encryption failed", e);
      throw new RuntimeException("Encryption failed", e);
    }
  }

  @Override
  public String decrypt(String encryptedValue) {
    try {
      // Decode and extract IV + encrypted data
      byte[] decoded = Base64.getDecoder().decode(encryptedValue);
      ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);

      byte[] encryptedBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(encryptedBytes);

      // Decrypt using AES-GCM
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
      byte[] decrypted = cipher.doFinal(encryptedBytes);

      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Decryption failed", e);
      throw new RuntimeException("Decryption failed", e);
    }
  }

  @Override
  public String generateDigest(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      log.error("Digest generation failed", e);
      throw new RuntimeException("Digest generation failed", e);
    }
  }

  @Override
  public boolean verify(String plainValue, String salt, String storedDigest) {
    try {
      String computedDigest = generateDigest(plainValue + salt);
      return computedDigest.equals(storedDigest);
    } catch (Exception e) {
      log.error("Verification failed", e);
      return false;
    }
  }
}

