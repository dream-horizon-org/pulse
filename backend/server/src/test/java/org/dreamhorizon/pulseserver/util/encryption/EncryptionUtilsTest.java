package org.dreamhorizon.pulseserver.util.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncryptionUtilsTest {

  private static final String VALID_ENCRYPTION_KEY;

  static {
    try {
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256);
      SecretKey key = keyGen.generateKey();
      VALID_ENCRYPTION_KEY = Base64.getEncoder().encodeToString(key.getEncoded());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Mock
  ApplicationConfig applicationConfig;

  @Nested
  class ClickhousePasswordEncryptionUtilTest {

    ClickhousePasswordEncryptionUtil util;

    @BeforeEach
    void setUp() {
      when(applicationConfig.getEncryptionMasterKey()).thenReturn(VALID_ENCRYPTION_KEY);
      util = new ClickhousePasswordEncryptionUtil(applicationConfig);
    }

    @Test
    void shouldThrowWhenEncryptionKeyIsNull() {
      when(applicationConfig.getEncryptionMasterKey()).thenReturn(null);
      assertThatThrownBy(() -> new ClickhousePasswordEncryptionUtil(applicationConfig))
          .hasMessageContaining("Encryption key initialization failed");
    }

    @Test
    void shouldThrowWhenEncryptionKeyIsBlank() {
      when(applicationConfig.getEncryptionMasterKey()).thenReturn("   ");
      assertThatThrownBy(() -> new ClickhousePasswordEncryptionUtil(applicationConfig))
          .hasMessageContaining("Encryption key initialization failed");
    }

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
      String plain = "my-secret-password";
      EncryptedData encrypted = util.encrypt(plain);
      assertThat(encrypted.getEncryptedValue()).isNotBlank();
      assertThat(encrypted.getSalt()).isNotBlank();
      assertThat(encrypted.getDigest()).isNotBlank();

      String decrypted = util.decrypt(encrypted.getEncryptedValue());
      assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void shouldGenerateDigest() {
      String digest = util.generateDigest("input");
      assertThat(digest).isNotBlank();
      assertThat(digest).matches("[A-Za-z0-9+/=]+");
    }

    @Test
    void shouldVerifyWhenPlainMatchesDigest() {
      String plain = "secret";
      String salt = Base64.getEncoder().encodeToString(new byte[16]);
      String digest = util.generateDigest(plain + salt);
      assertThat(util.verify(plain, salt, digest)).isTrue();
    }

    @Test
    void shouldNotVerifyWhenPlainDoesNotMatch() {
      String digest = util.generateDigest("secret" + "somesalt");
      assertThat(util.verify("wrong", "somesalt", digest)).isFalse();
    }

    @Test
    void shouldProduceDifferentCiphertextEachTime() {
      String plain = "password";
      EncryptedData enc1 = util.encrypt(plain);
      EncryptedData enc2 = util.encrypt(plain);
      assertThat(enc1.getEncryptedValue()).isNotEqualTo(enc2.getEncryptedValue());
      assertThat(util.decrypt(enc1.getEncryptedValue())).isEqualTo(plain);
      assertThat(util.decrypt(enc2.getEncryptedValue())).isEqualTo(plain);
    }

    @Test
    void shouldThrowWhenDecryptingInvalidData() {
      assertThatThrownBy(() -> util.decrypt("not-valid-base64!!!~~"))
          .hasMessageContaining("Decryption failed");
    }
  }

  @Nested
  class ProjectApiKeyEncryptionUtilTest {

    ProjectApiKeyEncryptionUtil util;

    @BeforeEach
    void setUp() {
      when(applicationConfig.getEncryptionMasterKey()).thenReturn(VALID_ENCRYPTION_KEY);
      util = new ProjectApiKeyEncryptionUtil(applicationConfig);
    }

    @Test
    void shouldThrowWhenEncryptionKeyIsNull() {
      when(applicationConfig.getEncryptionMasterKey()).thenReturn(null);
      assertThatThrownBy(() -> new ProjectApiKeyEncryptionUtil(applicationConfig))
          .hasMessageContaining("Encryption key initialization failed");
    }

    @Test
    void shouldThrowWhenEncryptionKeyIsBlank() {
      when(applicationConfig.getEncryptionMasterKey()).thenReturn("");
      assertThatThrownBy(() -> new ProjectApiKeyEncryptionUtil(applicationConfig))
          .hasMessageContaining("Encryption key initialization failed");
    }

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
      String plain = "pulse_proj_123_sk_abc123xyz";
      EncryptedData encrypted = util.encrypt(plain);
      assertThat(encrypted.getEncryptedValue()).isNotBlank();
      String decrypted = util.decrypt(encrypted.getEncryptedValue());
      assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void shouldVerifyCorrectly() {
      String plain = "api-key-value";
      String salt = Base64.getEncoder().encodeToString(new byte[16]);
      String digest = util.generateDigest(plain + salt);
      assertThat(util.verify(plain, salt, digest)).isTrue();
      assertThat(util.verify("wrong", salt, digest)).isFalse();
    }
  }
}
