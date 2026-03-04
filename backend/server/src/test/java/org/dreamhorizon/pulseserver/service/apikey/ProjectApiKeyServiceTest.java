package org.dreamhorizon.pulseserver.service.apikey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyDao;
import org.dreamhorizon.pulseserver.dao.apikey.models.ProjectApiKey;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.CreateApiKeyRequest;
import org.dreamhorizon.pulseserver.service.apikey.models.RevokeApiKeyRequest;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ProjectApiKeyServiceTest {

  @Mock
  ProjectApiKeyDao apiKeyDao;

  @Mock
  ProjectDao projectDao;

  @Mock
  ProjectApiKeyEncryptionUtil encryptionUtil;

  @Mock
  SqlConnection sqlConnection;

  ProjectApiKeyService apiKeyService;

  @BeforeEach
  void setup() {
    apiKeyService = new ProjectApiKeyService(apiKeyDao, projectDao, encryptionUtil);
  }

  private ProjectApiKey createMockApiKey() {
    return ProjectApiKey.builder()
        .projectApiKeyId(1L)
        .projectId("test-project")
        .displayName("Default")
        .apiKeyEncrypted("encrypted_value")
        .encryptionSalt("salt123")
        .apiKeyDigest("digest123")
        .isActive(true)
        .expiresAt(null)
        .gracePeriodEndsAt(null)
        .createdBy("user@example.com")
        .createdAt(Instant.now())
        .build();
  }

  private EncryptedData createMockEncryptedData() {
    return EncryptedData.builder()
        .encryptedValue("encrypted_api_key")
        .salt("random_salt")
        .digest("digest_hash")
        .build();
  }

  // ==================== CREATE API KEY TESTS ====================

  @Nested
  class TestCreateApiKey {

    @Test
    void shouldCreateApiKeySuccessfully() {
      CreateApiKeyRequest request = CreateApiKeyRequest.builder()
          .projectId("test-project")
          .displayName("My API Key")
          .createdBy("user@example.com")
          .build();

      ProjectApiKey mockApiKey = createMockApiKey();
      mockApiKey.setDisplayName("My API Key");

      when(projectDao.projectExists("test-project")).thenReturn(Single.just(true));
      when(encryptionUtil.encrypt(anyString())).thenReturn(createMockEncryptedData());
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest_hash");
      when(apiKeyDao.createApiKey(
          eq("test-project"),
          eq("My API Key"),
          anyString(),
          anyString(),
          anyString(),
          isNull(),
          eq("user@example.com")
      )).thenReturn(Single.just(mockApiKey));

      ApiKeyInfo result = apiKeyService.createApiKey(request).blockingGet();

      assertNotNull(result);
      assertEquals("test-project", result.getProjectId());
      assertEquals("My API Key", result.getDisplayName());
      assertNotNull(result.getRawApiKey());
      assertTrue(result.getIsActive());
      verify(apiKeyDao).createApiKey(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void shouldGenerateApiKeyInCorrectFormat() {
      CreateApiKeyRequest request = CreateApiKeyRequest.builder()
          .projectId("my-proj-123")
          .displayName("Test Key")
          .createdBy("admin@example.com")
          .build();

      when(projectDao.projectExists("my-proj-123")).thenReturn(Single.just(true));
      when(encryptionUtil.encrypt(anyString())).thenAnswer(invocation -> {
        String rawKey = invocation.getArgument(0);
        assertTrue(rawKey.startsWith("my-proj-123_"));
        assertEquals(24 + "my-proj-123_".length(), rawKey.length());
        return createMockEncryptedData();
      });
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest");
      when(apiKeyDao.createApiKey(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
          .thenReturn(Single.just(createMockApiKey()));

      apiKeyService.createApiKey(request).blockingGet();

      verify(encryptionUtil).encrypt(anyString());
    }

    @Test
    void shouldCreateApiKeyWithExpiration() {
      Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
      CreateApiKeyRequest request = CreateApiKeyRequest.builder()
          .projectId("test-project")
          .displayName("Expiring Key")
          .expiresAt(expiresAt)
          .createdBy("user@example.com")
          .build();

      ProjectApiKey mockApiKey = createMockApiKey();
      mockApiKey.setExpiresAt(expiresAt);

      when(projectDao.projectExists("test-project")).thenReturn(Single.just(true));
      when(encryptionUtil.encrypt(anyString())).thenReturn(createMockEncryptedData());
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest");
      when(apiKeyDao.createApiKey(anyString(), anyString(), anyString(), anyString(), anyString(), eq(expiresAt), anyString()))
          .thenReturn(Single.just(mockApiKey));

      ApiKeyInfo result = apiKeyService.createApiKey(request).blockingGet();

      assertNotNull(result);
      assertEquals(expiresAt, result.getExpiresAt());
    }

    @Test
    void shouldFailWhenProjectNotFound() {
      CreateApiKeyRequest request = CreateApiKeyRequest.builder()
          .projectId("non-existent")
          .displayName("Test Key")
          .createdBy("user@example.com")
          .build();

      when(projectDao.projectExists("non-existent")).thenReturn(Single.just(false));

      Exception ex = assertThrows(RuntimeException.class,
          () -> apiKeyService.createApiKey(request).blockingGet());
      assertTrue(ex.getMessage().contains("Project not found"));
      verify(apiKeyDao, never()).createApiKey(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void shouldEncryptApiKeyBeforeStoring() {
      CreateApiKeyRequest request = CreateApiKeyRequest.builder()
          .projectId("test-project")
          .displayName("Test")
          .createdBy("user@example.com")
          .build();

      EncryptedData encryptedData = createMockEncryptedData();
      when(projectDao.projectExists("test-project")).thenReturn(Single.just(true));
      when(encryptionUtil.encrypt(anyString())).thenReturn(encryptedData);
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest");
      when(apiKeyDao.createApiKey(
          anyString(),
          anyString(),
          eq("encrypted_api_key"),
          eq("random_salt"),
          anyString(),
          any(),
          anyString()
      )).thenReturn(Single.just(createMockApiKey()));

      apiKeyService.createApiKey(request).blockingGet();

      verify(encryptionUtil).encrypt(anyString());
      verify(apiKeyDao).createApiKey(anyString(), anyString(), eq("encrypted_api_key"), eq("random_salt"), anyString(), any(), anyString());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      CreateApiKeyRequest request = CreateApiKeyRequest.builder()
          .projectId("test-project")
          .displayName("Test")
          .createdBy("user@example.com")
          .build();

      when(projectDao.projectExists("test-project")).thenReturn(Single.just(true));
      when(encryptionUtil.encrypt(anyString())).thenReturn(createMockEncryptedData());
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest");
      when(apiKeyDao.createApiKey(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
          .thenReturn(Single.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> apiKeyService.createApiKey(request).blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  // ==================== CREATE DEFAULT API KEY (TRANSACTIONAL) TESTS ====================

  @Nested
  class TestCreateDefaultApiKey {

    @Test
    void shouldCreateDefaultApiKeyWithinTransaction() {
      when(encryptionUtil.encrypt(anyString())).thenReturn(createMockEncryptedData());
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest");
      when(apiKeyDao.createApiKey(
          eq(sqlConnection),
          eq("test-project"),
          eq("Default"),
          anyString(),
          anyString(),
          anyString(),
          isNull(),
          eq("creator@example.com")
      )).thenReturn(Single.just(createMockApiKey()));

      ApiKeyInfo result = apiKeyService.createDefaultApiKey(sqlConnection, "test-project", "creator@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("Default", result.getDisplayName());
      assertNotNull(result.getRawApiKey());
      verify(apiKeyDao).createApiKey(eq(sqlConnection), anyString(), eq("Default"), anyString(), anyString(), anyString(), isNull(), anyString());
    }

    @Test
    void shouldSetNoExpirationForDefaultKey() {
      when(encryptionUtil.encrypt(anyString())).thenReturn(createMockEncryptedData());
      when(encryptionUtil.generateDigest(anyString())).thenReturn("digest");
      when(apiKeyDao.createApiKey(
          eq(sqlConnection),
          anyString(),
          anyString(),
          anyString(),
          anyString(),
          anyString(),
          isNull(),
          anyString()
      )).thenReturn(Single.just(createMockApiKey()));

      apiKeyService.createDefaultApiKey(sqlConnection, "test-project", "creator@example.com").blockingGet();

      verify(apiKeyDao).createApiKey(any(SqlConnection.class), anyString(), anyString(), anyString(), anyString(), anyString(), isNull(), anyString());
    }
  }

  // ==================== GET ACTIVE API KEYS TESTS ====================

  @Nested
  class TestGetActiveApiKeys {

    @Test
    void shouldGetActiveApiKeysSuccessfully() {
      ProjectApiKey key1 = createMockApiKey();
      ProjectApiKey key2 = createMockApiKey();
      key2.setProjectApiKeyId(2L);
      key2.setDisplayName("Secondary Key");

      when(apiKeyDao.getActiveApiKeysByProjectId("test-project"))
          .thenReturn(Flowable.just(key1, key2));
      when(encryptionUtil.decrypt(anyString())).thenReturn("decrypted_key");

      List<ApiKeyPublicInfo> result = apiKeyService.getActiveApiKeys("test-project").blockingGet();

      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoActiveKeys() {
      when(apiKeyDao.getActiveApiKeysByProjectId("test-project"))
          .thenReturn(Flowable.empty());

      List<ApiKeyPublicInfo> result = apiKeyService.getActiveApiKeys("test-project").blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldDecryptKeysForPublicInfo() {
      ProjectApiKey mockKey = createMockApiKey();
      when(apiKeyDao.getActiveApiKeysByProjectId("test-project"))
          .thenReturn(Flowable.just(mockKey));
      when(encryptionUtil.decrypt("encrypted_value")).thenReturn("test-project_abc123");

      List<ApiKeyPublicInfo> result = apiKeyService.getActiveApiKeys("test-project").blockingGet();

      assertEquals(1, result.size());
      assertEquals("test-project_abc123", result.get(0).getRawApiKey());
      verify(encryptionUtil).decrypt("encrypted_value");
    }
  }

  // ==================== REVOKE API KEY TESTS ====================

  @Nested
  class TestRevokeApiKey {

    @Test
    void shouldRevokeApiKeySuccessfully() {
      RevokeApiKeyRequest request = RevokeApiKeyRequest.builder()
          .apiKeyId(1L)
          .projectId("test-project")
          .revokedBy("admin@example.com")
          .build();

      when(apiKeyDao.deactivateApiKey(eq(1L), eq("test-project"), eq("admin@example.com"), eq("revoked"), any(Instant.class)))
          .thenReturn(Completable.complete());

      apiKeyService.revokeApiKey(request).blockingAwait();

      verify(apiKeyDao).deactivateApiKey(eq(1L), eq("test-project"), eq("admin@example.com"), eq("revoked"), any(Instant.class));
    }

    @Test
    void shouldRevokeApiKeyWithGracePeriod() {
      RevokeApiKeyRequest request = RevokeApiKeyRequest.builder()
          .apiKeyId(1L)
          .projectId("test-project")
          .revokedBy("admin@example.com")
          .gracePeriodDays(7)
          .build();

      ArgumentCaptor<Instant> gracePeriodCaptor = ArgumentCaptor.forClass(Instant.class);
      when(apiKeyDao.deactivateApiKey(eq(1L), eq("test-project"), eq("admin@example.com"), eq("revoked"), gracePeriodCaptor.capture()))
          .thenReturn(Completable.complete());

      apiKeyService.revokeApiKey(request).blockingAwait();

      Instant capturedGracePeriod = gracePeriodCaptor.getValue();
      Instant expectedMinGracePeriod = Instant.now().plus(6, ChronoUnit.DAYS);
      Instant expectedMaxGracePeriod = Instant.now().plus(8, ChronoUnit.DAYS);
      assertTrue(capturedGracePeriod.isAfter(expectedMinGracePeriod));
      assertTrue(capturedGracePeriod.isBefore(expectedMaxGracePeriod));
    }

    @Test
    void shouldDefaultGracePeriodToZeroWhenNull() {
      RevokeApiKeyRequest request = RevokeApiKeyRequest.builder()
          .apiKeyId(1L)
          .projectId("test-project")
          .revokedBy("admin@example.com")
          .gracePeriodDays(null)
          .build();

      ArgumentCaptor<Instant> gracePeriodCaptor = ArgumentCaptor.forClass(Instant.class);
      when(apiKeyDao.deactivateApiKey(eq(1L), eq("test-project"), anyString(), anyString(), gracePeriodCaptor.capture()))
          .thenReturn(Completable.complete());

      apiKeyService.revokeApiKey(request).blockingAwait();

      Instant capturedGracePeriod = gracePeriodCaptor.getValue();
      Instant expectedMin = Instant.now().minus(1, ChronoUnit.MINUTES);
      Instant expectedMax = Instant.now().plus(1, ChronoUnit.MINUTES);
      assertTrue(capturedGracePeriod.isAfter(expectedMin) && capturedGracePeriod.isBefore(expectedMax));
    }

    @Test
    void shouldFailWhenApiKeyNotFound() {
      RevokeApiKeyRequest request = RevokeApiKeyRequest.builder()
          .apiKeyId(999L)
          .projectId("test-project")
          .revokedBy("admin@example.com")
          .build();

      when(apiKeyDao.deactivateApiKey(eq(999L), anyString(), anyString(), anyString(), any(Instant.class)))
          .thenReturn(Completable.error(new RuntimeException("API key not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> apiKeyService.revokeApiKey(request).blockingAwait());
      assertTrue(ex.getMessage().contains("API key not found"));
    }
  }

  // ==================== GET ALL VALID API KEYS (INTERNAL) TESTS ====================

  @Nested
  class TestGetAllValidApiKeys {

    @Test
    void shouldGetAllValidApiKeysSuccessfully() {
      ProjectApiKey key1 = createMockApiKey();
      ProjectApiKey key2 = createMockApiKey();
      key2.setProjectApiKeyId(2L);
      key2.setIsActive(false);
      key2.setGracePeriodEndsAt(Instant.now().plus(1, ChronoUnit.DAYS));

      when(apiKeyDao.getAllValidApiKeys()).thenReturn(Flowable.just(key1, key2));
      when(encryptionUtil.decrypt(anyString())).thenReturn("decrypted_key");

      List<ApiKeyInfo> result = apiKeyService.getAllValidApiKeys().toList().blockingGet();

      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    void shouldDecryptApiKeysForInternalUse() {
      ProjectApiKey mockKey = createMockApiKey();
      when(apiKeyDao.getAllValidApiKeys()).thenReturn(Flowable.just(mockKey));
      when(encryptionUtil.decrypt("encrypted_value")).thenReturn("test-project_secretkey");

      List<ApiKeyInfo> result = apiKeyService.getAllValidApiKeys().toList().blockingGet();

      assertEquals(1, result.size());
      assertEquals("test-project_secretkey", result.get(0).getRawApiKey());
    }

    @Test
    void shouldReturnEmptyWhenNoValidKeys() {
      when(apiKeyDao.getAllValidApiKeys()).thenReturn(Flowable.empty());

      List<ApiKeyInfo> result = apiKeyService.getAllValidApiKeys().toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  // ==================== MAPPING TESTS ====================

  @Nested
  class TestApiKeyMapping {

    @Test
    void shouldMapAllFieldsToApiKeyInfo() {
      Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
      Instant gracePeriodEndsAt = Instant.now().plus(7, ChronoUnit.DAYS);
      Instant deactivatedAt = Instant.now();

      ProjectApiKey apiKey = ProjectApiKey.builder()
          .projectApiKeyId(42L)
          .projectId("my-project")
          .displayName("Production Key")
          .apiKeyEncrypted("encrypted")
          .isActive(false)
          .expiresAt(expiresAt)
          .gracePeriodEndsAt(gracePeriodEndsAt)
          .createdBy("creator@example.com")
          .createdAt(Instant.now())
          .deactivatedAt(deactivatedAt)
          .deactivatedBy("admin@example.com")
          .deactivationReason("revoked")
          .build();

      when(apiKeyDao.getAllValidApiKeys()).thenReturn(Flowable.just(apiKey));
      when(encryptionUtil.decrypt("encrypted")).thenReturn("raw_key");

      ApiKeyInfo result = apiKeyService.getAllValidApiKeys().blockingFirst();

      assertEquals(42L, result.getApiKeyId());
      assertEquals("my-project", result.getProjectId());
      assertEquals("Production Key", result.getDisplayName());
      assertEquals("raw_key", result.getRawApiKey());
      assertNotNull(result.getExpiresAt());
      assertNotNull(result.getGracePeriodEndsAt());
      assertEquals("creator@example.com", result.getCreatedBy());
      assertEquals("admin@example.com", result.getDeactivatedBy());
      assertEquals("revoked", result.getDeactivationReason());
    }
  }
}
