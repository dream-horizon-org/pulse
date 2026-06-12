package org.dreamhorizon.pulseserver.util.encryption;

/**
 * Constants for AES-GCM encryption used across the application.
 */
public final class EncryptionConstants {

  private EncryptionConstants() {
    // Utility class, prevent instantiation
  }

  /** AES algorithm identifier */
  public static final String ALGORITHM = "AES";

  /** AES-GCM transformation string */
  public static final String TRANSFORMATION = "AES/GCM/NoPadding";

  /** SHA-256 for digest generation */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** GCM IV length in bytes (96 bits recommended for GCM) */
  public static final int GCM_IV_LENGTH = 12;

  /** GCM authentication tag length in bits */
  public static final int GCM_TAG_LENGTH = 128;

  /** Salt length in bytes for digest */
  public static final int SALT_LENGTH = 16;
}

