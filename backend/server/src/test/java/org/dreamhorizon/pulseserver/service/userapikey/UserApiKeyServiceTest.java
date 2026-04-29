package org.dreamhorizon.pulseserver.service.userapikey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.userapikey.UserApiKeyDao;
import org.dreamhorizon.pulseserver.dao.userapikey.models.UserApiKey;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyInfo;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserApiKeyServiceTest {

  @Mock
  UserApiKeyDao userApiKeyDao;

  @Mock
  ProjectApiKeyEncryptionUtil encryptionUtil;

  UserApiKeyService service;

  @BeforeEach
  void setup() {
    service = new UserApiKeyService(userApiKeyDao, encryptionUtil);
  }

  private UserApiKey daoKeyWithCreatedAt(Instant createdAt) {
    return UserApiKey.builder()
        .id(10L)
        .userId("user-1")
        .displayName("Key one")
        .apiKeyHash("stored-hash")
        .keyPrefix("pulse_mcp_0123456789")
        .isActive(true)
        .createdAt(createdAt)
        .build();
  }

  @Nested
  class CreateApiKey {

    @Test
    void shouldCreateAndReturnRawKeyWithPrefixFromDao() {
      Instant created = Instant.parse("2025-01-01T00:00:00Z");
      UserApiKey fromDao = daoKeyWithCreatedAt(created);
      when(encryptionUtil.generateDigest(anyString())).thenReturn("stored-hash");
      when(userApiKeyDao.createApiKey(eq("user-1"), eq("My MCP"), eq("stored-hash"), anyString()))
          .thenReturn(Single.just(fromDao));

      UserApiKeyInfo result = service.createApiKey("user-1", "My MCP").blockingGet();

      assertNotNull(result);
      assertEquals(10L, result.getId());
      assertEquals("Key one", result.getDisplayName());
      assertEquals("pulse_mcp_0123456789", result.getKeyPrefix());
      assertEquals(created, result.getCreatedAt());
      assertNotNull(result.getRawApiKey());
      assertTrue(result.getRawApiKey().startsWith("pulse_mcp_"));
      assertEquals(20, result.getKeyPrefix().length());
      ArgumentCaptor<String> prefixCap = ArgumentCaptor.forClass(String.class);
      verify(userApiKeyDao).createApiKey(eq("user-1"), eq("My MCP"), eq("stored-hash"), prefixCap.capture());
      assertEquals(20, prefixCap.getValue().length());
    }

    @Test
    void shouldUseNowWhenDaoCreatedAtIsNull() {
      UserApiKey fromDao = daoKeyWithCreatedAt(null);
      when(encryptionUtil.generateDigest(anyString())).thenReturn("h");
      when(userApiKeyDao.createApiKey(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(Single.just(fromDao));

      UserApiKeyInfo result = service.createApiKey("user-1", "K").blockingGet();

      assertNotNull(result.getCreatedAt());
    }

    @Test
    void shouldPropagateDaoError() {
      when(encryptionUtil.generateDigest(anyString())).thenReturn("h");
      when(userApiKeyDao.createApiKey(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(Single.error(new RuntimeException("db down")));

      assertThrows(RuntimeException.class, () -> service.createApiKey("u", "d").blockingGet());
    }
  }

  @Nested
  class ListApiKeys {

    @Test
    void shouldMapToPublicInfo() {
      UserApiKey k = daoKeyWithCreatedAt(Instant.now());
      when(userApiKeyDao.findActiveByUser("user-1")).thenReturn(Single.just(List.of(k)));

      List<UserApiKeyPublicInfo> result = service.listApiKeys("user-1").blockingGet();

      assertEquals(1, result.size());
      assertEquals(10L, result.get(0).getId());
      assertEquals("Key one", result.get(0).getDisplayName());
      assertEquals("pulse_mcp_0123456789", result.get(0).getKeyPrefix());
      assertTrue(result.get(0).getIsActive());
    }

    @Test
    void shouldPropagateListError() {
      when(userApiKeyDao.findActiveByUser("u")).thenReturn(Single.error(new RuntimeException("list fail")));

      assertThrows(RuntimeException.class, () -> service.listApiKeys("u").blockingGet());
    }
  }

  @Nested
  class RevokeApiKey {

    @Test
    void shouldDelegateToDao() {
      when(userApiKeyDao.revoke(3L, "user-1", "user-1")).thenReturn(Completable.complete());

      service.revokeApiKey(3L, "user-1", "user-1").blockingAwait();

      verify(userApiKeyDao).revoke(3L, "user-1", "user-1");
    }
  }

  @Nested
  class ValidateAndGetUserId {

    @Test
    void shouldReturnUserIdWhenHashMatches() {
      UserApiKey k = UserApiKey.builder().userId("uid-9").build();
      when(encryptionUtil.generateDigest("secret")).thenReturn("digest");
      when(userApiKeyDao.findActiveByHash("digest")).thenReturn(Maybe.just(k));

      String uid = service.validateAndGetUserId("secret").blockingGet();

      assertEquals("uid-9", uid);
    }

    @Test
    void shouldBeEmptyWhenUnknownKey() {
      when(encryptionUtil.generateDigest("bad")).thenReturn("d");
      when(userApiKeyDao.findActiveByHash("d")).thenReturn(Maybe.empty());

      assertTrue(service.validateAndGetUserId("bad").isEmpty().blockingGet());
    }
  }
}
