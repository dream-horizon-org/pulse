package org.dreamhorizon.pulseserver.util.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Holds the result of an encryption operation.
 */
@Data
@Builder
public class EncryptedData {

  /** Base64-encoded encrypted value (includes IV prepended) */
  private String encryptedValue;

  /** Base64-encoded random salt used for digest */
  private String salt;

  /** SHA-256 digest of (plainValue + salt) for verification */
  private String digest;
}

