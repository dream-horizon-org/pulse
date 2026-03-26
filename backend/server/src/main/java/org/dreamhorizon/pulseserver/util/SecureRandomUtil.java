package org.dreamhorizon.pulseserver.util;

import java.security.SecureRandom;

/**
 * Utility class for generating cryptographically secure random values.
 */
public final class SecureRandomUtil {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Alphanumeric characters (A-Z, a-z, 0-9) for random string generation */
  private static final String ALPHANUMERIC =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private SecureRandomUtil() {
    // Utility class, prevent instantiation
  }

  /**
   * Generates a cryptographically secure alphanumeric string.
   *
   * @param length the desired length of the string
   * @return a random alphanumeric string (A-Z, a-z, 0-9)
   */
  public static String generateAlphanumeric(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("Length must be positive");
    }
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
    }
    return sb.toString();
  }

  /**
   * Generates random bytes for cryptographic purposes (salts, IVs, etc.).
   *
   * @param length the number of bytes to generate
   * @return array of random bytes
   */
  public static byte[] generateBytes(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("Length must be positive");
    }
    byte[] bytes = new byte[length];
    SECURE_RANDOM.nextBytes(bytes);
    return bytes;
  }
}

