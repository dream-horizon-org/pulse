package org.dreamhorizon.pulseserver.util;

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
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;

@Slf4j
@Singleton
public class PasswordEncryptionUtil {
  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String DIGEST_ALGORITHM = "SHA-256";
  private static final int GCM_IV_LENGTH = 12;  // 96 bits recommended for GCM
  private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

  private final SecretKey secretKey;
  private final SecureRandom secureRandom;

  @Inject
  public PasswordEncryptionUtil(ClickhouseConfig clickhouseConfig) {
    try {
      String encryptionKey = clickhouseConfig.getEncryptionMasterKey();
      if (encryptionKey == null || encryptionKey.isBlank()) {
        throw new IllegalStateException("encryptionMasterKey is not configured in ClickhouseConfig");
      }
      byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
      this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
      this.secureRandom = new SecureRandom();
      log.info("Encryption key initialized successfully from ClickhouseConfig (AES-GCM)");
    } catch (Exception e) {
      log.error("Failed to initialize encryption key", e);
      throw new RuntimeException("Encryption key initialization failed", e);
    }
  }

  public EncryptedPassword encryptPassword(String plainPassword) {
    try {
      // Generate random salt for digest
      byte[] salt = new byte[16];
      secureRandom.nextBytes(salt);
      String saltBase64 = Base64.getEncoder().encodeToString(salt);

      // Generate random IV for GCM
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      // Encrypt using AES-GCM
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
      byte[] encryptedPassword = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));

      // Combine IV + encrypted data for storage
      ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedPassword.length);
      byteBuffer.put(iv);
      byteBuffer.put(encryptedPassword);
      String encryptedBase64 = Base64.getEncoder().encodeToString(byteBuffer.array());

      String digest = generateDigest(plainPassword + saltBase64);

      return EncryptedPassword.builder()
          .encryptedPassword(encryptedBase64)
          .salt(saltBase64)
          .digest(digest)
          .build();
    } catch (Exception e) {
      log.error("Password encryption failed", e);
      throw new RuntimeException("Password encryption failed", e);
    }
  }

  public String decryptPassword(String encryptedPasswordBase64) {
    try {
      // Decode and extract IV + encrypted data
      byte[] decoded = Base64.getDecoder().decode(encryptedPasswordBase64);
      ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);

      byte[] encryptedPassword = new byte[byteBuffer.remaining()];
      byteBuffer.get(encryptedPassword);

      // Decrypt using AES-GCM
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
      byte[] decrypted = cipher.doFinal(encryptedPassword);

      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Password decryption failed", e);
      throw new RuntimeException("Password decryption failed", e);
    }
  }

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

  public boolean verifyPassword(String plainPassword, String salt, String storedDigest) {
    try {
      String computedDigest = generateDigest(plainPassword + salt);
      return computedDigest.equals(storedDigest);
    } catch (Exception e) {
      log.error("Password verification failed", e);
      return false;
    }
  }

  @Data
  @Builder
  public static class EncryptedPassword {
    private String encryptedPassword;
    private String salt;
    private String digest;
  }
}