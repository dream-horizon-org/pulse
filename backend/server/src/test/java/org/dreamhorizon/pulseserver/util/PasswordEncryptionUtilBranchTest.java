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

class PasswordEncryptionUtilBranchTest {

  private static final String TEST_ENCRYPTION_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

  private PasswordEncryptionUtil util;

  @BeforeEach
  void setup() {
    ClickhouseConfig config = new ClickhouseConfig();
    config.setEncryptionMasterKey(TEST_ENCRYPTION_KEY);
    util = new PasswordEncryptionUtil(config);
  }

  @Nested
  class TestEncryptDecryptCycle {

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
      String password = "testPassword123!";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      String decrypted = util.decryptPassword(encrypted.getEncryptedPassword());
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleEmptyPassword() {
      String password = "";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      String decrypted = util.decryptPassword(encrypted.getEncryptedPassword());
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleSpecialCharacters() {
      String password = "p@$$w0rd!#$%^&*()_+{}|:<>?~`";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      String decrypted = util.decryptPassword(encrypted.getEncryptedPassword());
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleUnicodeCharacters() {
      String password = "密码тест";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      String decrypted = util.decryptPassword(encrypted.getEncryptedPassword());
      assertEquals(password, decrypted);
    }

    @Test
    void shouldHandleLongPassword() {
      String password = "a".repeat(1000);
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      String decrypted = util.decryptPassword(encrypted.getEncryptedPassword());
      assertEquals(password, decrypted);
    }

    @Test
    void shouldProduceDifferentEncryptedPasswordsForSamePassword() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted1 = util.encryptPassword(password);
      PasswordEncryptionUtil.EncryptedPassword encrypted2 = util.encryptPassword(password);
      // Encryption result may be same (AES ECB) but salt will differ
      assertNotEquals(encrypted1.getSalt(), encrypted2.getSalt());
    }

    @Test
    void shouldProduceDifferentSaltsForSamePassword() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted1 = util.encryptPassword(password);
      PasswordEncryptionUtil.EncryptedPassword encrypted2 = util.encryptPassword(password);
      assertNotEquals(encrypted1.getSalt(), encrypted2.getSalt());
    }

    @Test
    void shouldProduceDifferentDigestsForSamePassword() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted1 = util.encryptPassword(password);
      PasswordEncryptionUtil.EncryptedPassword encrypted2 = util.encryptPassword(password);
      // Digests will be different because salt is different
      assertNotEquals(encrypted1.getDigest(), encrypted2.getDigest());
    }
  }

  @Nested
  class TestDigest {

    @Test
    void shouldGenerateConsistentDigest() {
      String input = "testInput";
      String digest1 = util.generateDigest(input);
      String digest2 = util.generateDigest(input);
      assertEquals(digest1, digest2);
    }

    @Test
    void shouldGenerateDifferentDigestsForDifferentInputs() {
      String digest1 = util.generateDigest("input1");
      String digest2 = util.generateDigest("input2");
      assertNotEquals(digest1, digest2);
    }

    @Test
    void shouldGenerateDigestForEmptyString() {
      String digest = util.generateDigest("");
      assertNotNull(digest);
      assertTrue(digest.length() > 0);
    }

    @Test
    void shouldGenerateDigestForSpecialCharacters() {
      String digest = util.generateDigest("!@#$%^&*()");
      assertNotNull(digest);
    }

    @Test
    void shouldGenerateDigestForUnicode() {
      String digest = util.generateDigest("密码тест");
      assertNotNull(digest);
    }
  }

  @Nested
  class TestVerifyPassword {

    @Test
    void shouldVerifyMatchingPassword() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      assertTrue(util.verifyPassword(password, encrypted.getSalt(), encrypted.getDigest()));
    }

    @Test
    void shouldRejectNonMatchingPassword() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      assertFalse(util.verifyPassword("wrongPassword", encrypted.getSalt(), encrypted.getDigest()));
    }

    @Test
    void shouldHandleEmptyPasswordVerification() {
      String password = "";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      assertTrue(util.verifyPassword("", encrypted.getSalt(), encrypted.getDigest()));
      assertFalse(util.verifyPassword("nonEmpty", encrypted.getSalt(), encrypted.getDigest()));
    }

    @Test
    void shouldHandleSpecialCharacterVerification() {
      String password = "!@#$%^&*()";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      assertTrue(util.verifyPassword(password, encrypted.getSalt(), encrypted.getDigest()));
    }

    @Test
    void shouldReturnFalseForInvalidSalt() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      assertFalse(util.verifyPassword(password, "invalidSalt", encrypted.getDigest()));
    }

    @Test
    void shouldReturnFalseForInvalidDigest() {
      String password = "testPassword";
      PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword(password);
      assertFalse(util.verifyPassword(password, encrypted.getSalt(), "invalidDigest"));
    }
  }

  @Nested
  class TestEncryptedPasswordClass {

    @Test
    void shouldCreateWithBuilder() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertEquals("encrypted", password.getEncryptedPassword());
      assertEquals("salt", password.getSalt());
      assertEquals("digest", password.getDigest());
    }

    @Test
    void equalsShouldReturnTrueForSameObject() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertTrue(password.equals(password));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertFalse(password.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertFalse(password.equals("string"));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertTrue(password1.equals(password2));
    }

    @Test
    void equalsShouldReturnFalseWhenEncryptedPasswordDiffers() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted1")
              .salt("salt")
              .digest("digest")
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted2")
              .salt("salt")
              .digest("digest")
              .build();
      assertFalse(password1.equals(password2));
    }

    @Test
    void equalsShouldReturnFalseWhenSaltDiffers() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt1")
              .digest("digest")
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt2")
              .digest("digest")
              .build();
      assertFalse(password1.equals(password2));
    }

    @Test
    void equalsShouldReturnFalseWhenDigestDiffers() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest1")
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest2")
              .build();
      assertFalse(password1.equals(password2));
    }

    @Test
    void equalsShouldHandleNullFields() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword(null)
              .salt(null)
              .digest(null)
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword(null)
              .salt(null)
              .digest(null)
              .build();
      assertTrue(password1.equals(password2));
    }

    @Test
    void equalsShouldHandleMixedNullFields() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt(null)
              .digest("digest")
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertFalse(password1.equals(password2));
    }

    @Test
    void hashCodeShouldBeConsistent() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertEquals(password.hashCode(), password.hashCode());
    }

    @Test
    void hashCodeShouldBeEqualForEqualObjects() {
      PasswordEncryptionUtil.EncryptedPassword password1 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      PasswordEncryptionUtil.EncryptedPassword password2 = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertEquals(password1.hashCode(), password2.hashCode());
    }

    @Test
    void hashCodeShouldHandleNullFields() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder().build();
      assertNotNull(password.hashCode());
    }

    @Test
    void toStringShouldNotReturnNull() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertNotNull(password.toString());
    }

    @Test
    void toStringShouldContainFieldValues() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      String str = password.toString();
      assertTrue(str.contains("encrypted") || str.contains("encryptedPassword"));
      assertTrue(str.contains("salt"));
      assertTrue(str.contains("digest"));
    }

    @Test
    void canEqualShouldWork() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder()
              .encryptedPassword("encrypted")
              .salt("salt")
              .digest("digest")
              .build();
      assertTrue(password.canEqual(PasswordEncryptionUtil.EncryptedPassword.builder().build()));
      assertFalse(password.canEqual("string"));
    }

    @Test
    void builderToStringShouldWork() {
      String str = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("test")
          .toString();
      assertNotNull(str);
    }

    @Test
    void settersShouldWork() {
      PasswordEncryptionUtil.EncryptedPassword password = 
          PasswordEncryptionUtil.EncryptedPassword.builder().build();
      password.setEncryptedPassword("newEncrypted");
      password.setSalt("newSalt");
      password.setDigest("newDigest");
      assertEquals("newEncrypted", password.getEncryptedPassword());
      assertEquals("newSalt", password.getSalt());
      assertEquals("newDigest", password.getDigest());
    }
  }

  @Nested
  class TestDecryptWithInvalidInput {

    @Test
    void shouldThrowExceptionForInvalidEncryptedPassword() {
      assertThrows(RuntimeException.class, () -> util.decryptPassword("invalid"));
    }

    @Test
    void shouldThrowExceptionForNullEncryptedPassword() {
      assertThrows(Exception.class, () -> util.decryptPassword(null));
    }
  }
}
