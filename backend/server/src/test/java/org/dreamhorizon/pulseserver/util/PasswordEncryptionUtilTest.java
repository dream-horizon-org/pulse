package org.dreamhorizon.pulseserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordEncryptionUtilTest {

  private static final String TEST_ENCRYPTION_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

  private PasswordEncryptionUtil encryptionUtil;

  @BeforeEach
  void setup() {
    ClickhouseConfig config = new ClickhouseConfig();
    config.setEncryptionMasterKey(TEST_ENCRYPTION_KEY);
    encryptionUtil = new PasswordEncryptionUtil(config);
  }

  @Nested
  class TestConstructor {

    @Test
    void shouldThrowExceptionWhenEncryptionKeyIsNull() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setEncryptionMasterKey(null);

      assertThrows(RuntimeException.class, () -> new PasswordEncryptionUtil(config));
    }

    @Test
    void shouldThrowExceptionWhenEncryptionKeyIsEmpty() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setEncryptionMasterKey("");

      assertThrows(RuntimeException.class, () -> new PasswordEncryptionUtil(config));
    }

    @Test
    void shouldThrowExceptionWhenEncryptionKeyIsBlank() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setEncryptionMasterKey("   ");

      assertThrows(RuntimeException.class, () -> new PasswordEncryptionUtil(config));
    }

    @Test
    void shouldThrowExceptionWhenEncryptionKeyIsInvalidBase64() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setEncryptionMasterKey("not-valid-base64!!!");

      assertThrows(RuntimeException.class, () -> new PasswordEncryptionUtil(config));
    }

    @Test
    void shouldInitializeSuccessfullyWithValidKey() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setEncryptionMasterKey(TEST_ENCRYPTION_KEY);

      PasswordEncryptionUtil util = new PasswordEncryptionUtil(config);
      assertNotNull(util);
    }

    @Test
    void shouldThrowIllegalStateExceptionForNullKey() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setEncryptionMasterKey(null);

      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> new PasswordEncryptionUtil(config));
      assertTrue(ex.getCause() instanceof IllegalStateException || 
                 ex.getMessage().contains("Encryption key initialization failed"));
    }
  }

  @Nested
  class TestEncryptPassword {

    @Test
    void shouldEncryptPasswordSuccessfully() {
      String plainPassword = "TestPassword123!";

      PasswordEncryptionUtil.EncryptedPassword result = encryptionUtil.encryptPassword(plainPassword);

      assertNotNull(result);
      assertNotNull(result.getEncryptedPassword());
      assertNotNull(result.getSalt());
      assertNotNull(result.getDigest());

      // Encrypted password should be different from plain password
      assertNotEquals(plainPassword, result.getEncryptedPassword());
    }

    @Test
    void shouldGenerateUniqueSaltForEachEncryption() {
      String plainPassword = "TestPassword123!";

      PasswordEncryptionUtil.EncryptedPassword result1 = encryptionUtil.encryptPassword(plainPassword);
      PasswordEncryptionUtil.EncryptedPassword result2 = encryptionUtil.encryptPassword(plainPassword);

      // Salt should be different each time (random)
      assertNotEquals(result1.getSalt(), result2.getSalt());
      // Digest should also be different due to different salt
      assertNotEquals(result1.getDigest(), result2.getDigest());
    }

    @Test
    void shouldHandleEmptyPassword() {
      String emptyPassword = "";

      PasswordEncryptionUtil.EncryptedPassword result = encryptionUtil.encryptPassword(emptyPassword);

      assertNotNull(result);
      assertNotNull(result.getEncryptedPassword());
    }

    @Test
    void shouldHandleSpecialCharacters() {
      String specialPassword = "P@$$w0rd!#$%^&*()_+-=[]{}|;':\",./<>?";

      PasswordEncryptionUtil.EncryptedPassword result = encryptionUtil.encryptPassword(specialPassword);

      assertNotNull(result);
      assertNotNull(result.getEncryptedPassword());
    }

    @Test
    void shouldHandleUnicodeCharacters() {
      String unicodePassword = "密码测试123पासवर्ड";

      PasswordEncryptionUtil.EncryptedPassword result = encryptionUtil.encryptPassword(unicodePassword);

      assertNotNull(result);
      assertNotNull(result.getEncryptedPassword());
    }

    @Test
    void shouldHandleLongPassword() {
      String longPassword = "A".repeat(1000);

      PasswordEncryptionUtil.EncryptedPassword result = encryptionUtil.encryptPassword(longPassword);

      assertNotNull(result);
      assertNotNull(result.getEncryptedPassword());
    }
  }

  @Nested
  class TestDecryptPassword {

    @Test
    void shouldDecryptPasswordSuccessfully() {
      String plainPassword = "TestPassword123!";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(plainPassword);
      String decrypted = encryptionUtil.decryptPassword(encrypted.getEncryptedPassword());

      assertEquals(plainPassword, decrypted);
    }

    @Test
    void shouldDecryptEmptyPassword() {
      String emptyPassword = "";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(emptyPassword);
      String decrypted = encryptionUtil.decryptPassword(encrypted.getEncryptedPassword());

      assertEquals(emptyPassword, decrypted);
    }

    @Test
    void shouldDecryptSpecialCharacters() {
      String specialPassword = "P@$$w0rd!#$%^&*()";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(specialPassword);
      String decrypted = encryptionUtil.decryptPassword(encrypted.getEncryptedPassword());

      assertEquals(specialPassword, decrypted);
    }

    @Test
    void shouldDecryptUnicodeCharacters() {
      String unicodePassword = "密码测试123पासवर्ड";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(unicodePassword);
      String decrypted = encryptionUtil.decryptPassword(encrypted.getEncryptedPassword());

      assertEquals(unicodePassword, decrypted);
    }

    @Test
    void shouldThrowExceptionForInvalidEncryptedPassword() {
      String invalidEncrypted = "invalid_base64_data!!!";

      assertThrows(RuntimeException.class,
          () -> encryptionUtil.decryptPassword(invalidEncrypted));
    }

    @Test
    void shouldThrowExceptionForCorruptedData() {
      // Valid base64 but not encrypted with our key
      String corruptedData = "dGhpcyBpcyBub3QgZW5jcnlwdGVk";

      assertThrows(RuntimeException.class,
          () -> encryptionUtil.decryptPassword(corruptedData));
    }
  }

  @Nested
  class TestGenerateDigest {

    @Test
    void shouldGenerateDigestSuccessfully() {
      String input = "TestInput123";

      String digest = encryptionUtil.generateDigest(input);

      assertNotNull(digest);
      assertFalse(digest.isEmpty());
    }

    @Test
    void shouldGenerateSameDigestForSameInput() {
      String input = "TestInput123";

      String digest1 = encryptionUtil.generateDigest(input);
      String digest2 = encryptionUtil.generateDigest(input);

      assertEquals(digest1, digest2);
    }

    @Test
    void shouldGenerateDifferentDigestForDifferentInput() {
      String input1 = "TestInput1";
      String input2 = "TestInput2";

      String digest1 = encryptionUtil.generateDigest(input1);
      String digest2 = encryptionUtil.generateDigest(input2);

      assertNotEquals(digest1, digest2);
    }

    @Test
    void shouldHandleEmptyInput() {
      String emptyInput = "";

      String digest = encryptionUtil.generateDigest(emptyInput);

      assertNotNull(digest);
      assertFalse(digest.isEmpty());
    }

    @Test
    void shouldHandleSpecialCharacters() {
      String specialInput = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

      String digest = encryptionUtil.generateDigest(specialInput);

      assertNotNull(digest);
    }
  }

  @Nested
  class TestVerifyPassword {

    @Test
    void shouldVerifyCorrectPassword() {
      String plainPassword = "TestPassword123!";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(plainPassword);
      boolean isValid = encryptionUtil.verifyPassword(plainPassword, encrypted.getSalt(), encrypted.getDigest());

      assertTrue(isValid);
    }

    @Test
    void shouldRejectIncorrectPassword() {
      String correctPassword = "CorrectPassword123!";
      String wrongPassword = "WrongPassword456!";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(correctPassword);
      boolean isValid = encryptionUtil.verifyPassword(wrongPassword, encrypted.getSalt(), encrypted.getDigest());

      assertFalse(isValid);
    }

    @Test
    void shouldRejectWrongSalt() {
      String plainPassword = "TestPassword123!";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(plainPassword);
      boolean isValid = encryptionUtil.verifyPassword(plainPassword, "wrongsalt", encrypted.getDigest());

      assertFalse(isValid);
    }

    @Test
    void shouldRejectWrongDigest() {
      String plainPassword = "TestPassword123!";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(plainPassword);
      boolean isValid = encryptionUtil.verifyPassword(plainPassword, encrypted.getSalt(), "wrongdigest");

      assertFalse(isValid);
    }

    @Test
    void shouldVerifyEmptyPassword() {
      String emptyPassword = "";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(emptyPassword);
      boolean isValid = encryptionUtil.verifyPassword(emptyPassword, encrypted.getSalt(), encrypted.getDigest());

      assertTrue(isValid);
    }

    @Test
    void shouldVerifySpecialCharacterPassword() {
      String specialPassword = "P@$$w0rd!#$%";

      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(specialPassword);
      boolean isValid = encryptionUtil.verifyPassword(specialPassword, encrypted.getSalt(), encrypted.getDigest());

      assertTrue(isValid);
    }

    @Test
    void shouldReturnFalseOnNullPassword() {
      // Passing null values to trigger exception path and return false
      boolean result = encryptionUtil.verifyPassword(null, "salt", "digest");
      assertFalse(result);
    }

    @Test
    void shouldReturnFalseOnNullSalt() {
      boolean result = encryptionUtil.verifyPassword("password", null, "digest");
      assertFalse(result);
    }
  }

  @Nested
  class TestEncryptedPasswordModel {

    @Test
    void shouldBuildEncryptedPasswordModel() {
      PasswordEncryptionUtil.EncryptedPassword model = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("encrypted123")
          .salt("salt456")
          .digest("digest789")
          .build();

      assertEquals("encrypted123", model.getEncryptedPassword());
      assertEquals("salt456", model.getSalt());
      assertEquals("digest789", model.getDigest());
    }

    @Test
    void shouldSetAndGetProperties() {
      PasswordEncryptionUtil.EncryptedPassword model = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("encrypted")
          .salt("salt")
          .digest("digest")
          .build();

      assertEquals("encrypted", model.getEncryptedPassword());
      assertEquals("salt", model.getSalt());
      assertEquals("digest", model.getDigest());
    }

    @Test
    void shouldUseSetters() {
      PasswordEncryptionUtil.EncryptedPassword model = PasswordEncryptionUtil.EncryptedPassword.builder().build();
      model.setEncryptedPassword("enc");
      model.setSalt("s");
      model.setDigest("d");
      
      assertEquals("enc", model.getEncryptedPassword());
      assertEquals("s", model.getSalt());
      assertEquals("d", model.getDigest());
    }

    @Test
    void shouldImplementEquals() {
      PasswordEncryptionUtil.EncryptedPassword m1 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("d").build();
      PasswordEncryptionUtil.EncryptedPassword m2 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("d").build();
      PasswordEncryptionUtil.EncryptedPassword m3 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("x").salt("s").digest("d").build();

      assertEquals(m1, m2);
      assertEquals(m1, m1);
      assertNotEquals(m1, m3);
      assertNotEquals(m1, null);
      assertNotEquals(m1, "string");
    }

    @Test
    void shouldImplementHashCode() {
      PasswordEncryptionUtil.EncryptedPassword m1 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("d").build();
      PasswordEncryptionUtil.EncryptedPassword m2 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("d").build();
      assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      PasswordEncryptionUtil.EncryptedPassword model = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("d").build();
      String str = model.toString();
      assertNotNull(str);
      assertTrue(str.contains("encryptedPassword") || str.contains("salt") || str.contains("digest"));
    }

    @Test
    void shouldHandleNullFields() {
      PasswordEncryptionUtil.EncryptedPassword m1 = PasswordEncryptionUtil.EncryptedPassword.builder().build();
      PasswordEncryptionUtil.EncryptedPassword m2 = PasswordEncryptionUtil.EncryptedPassword.builder().build();
      assertEquals(m1, m2);
      assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      PasswordEncryptionUtil.EncryptedPassword base = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("d").build();
      
      assertNotEquals(base, PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("x").salt("s").digest("d").build());
      assertNotEquals(base, PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("x").digest("d").build());
      assertNotEquals(base, PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").salt("s").digest("x").build());
    }

    @Test
    void shouldHandleNullVsNonNull() {
      PasswordEncryptionUtil.EncryptedPassword m1 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword(null).build();
      PasswordEncryptionUtil.EncryptedPassword m2 = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("e").build();
      assertNotEquals(m1, m2);
      assertNotEquals(m2, m1);
    }
  }

  @Nested
  class TestEndToEndEncryption {

    @Test
    void shouldPerformCompleteEncryptionDecryptionCycle() {
      String originalPassword = "MySecurePassword123!@#";

      // Encrypt
      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(originalPassword);

      // Decrypt
      String decrypted = encryptionUtil.decryptPassword(encrypted.getEncryptedPassword());

      // Verify
      boolean isValid = encryptionUtil.verifyPassword(originalPassword, encrypted.getSalt(), encrypted.getDigest());

      assertEquals(originalPassword, decrypted);
      assertTrue(isValid);
    }

    @Test
    void shouldHandleMultiplePasswordsIndependently() {
      String password1 = "Password1";
      String password2 = "Password2";
      String password3 = "Password3";

      PasswordEncryptionUtil.EncryptedPassword enc1 = encryptionUtil.encryptPassword(password1);
      PasswordEncryptionUtil.EncryptedPassword enc2 = encryptionUtil.encryptPassword(password2);
      PasswordEncryptionUtil.EncryptedPassword enc3 = encryptionUtil.encryptPassword(password3);

      assertEquals(password1, encryptionUtil.decryptPassword(enc1.getEncryptedPassword()));
      assertEquals(password2, encryptionUtil.decryptPassword(enc2.getEncryptedPassword()));
      assertEquals(password3, encryptionUtil.decryptPassword(enc3.getEncryptedPassword()));

      assertTrue(encryptionUtil.verifyPassword(password1, enc1.getSalt(), enc1.getDigest()));
      assertTrue(encryptionUtil.verifyPassword(password2, enc2.getSalt(), enc2.getDigest()));
      assertTrue(encryptionUtil.verifyPassword(password3, enc3.getSalt(), enc3.getDigest()));
    }
  }
}
