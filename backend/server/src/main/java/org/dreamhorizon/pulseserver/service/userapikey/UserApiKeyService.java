package org.dreamhorizon.pulseserver.service.userapikey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.userapikey.UserApiKeyDao;
import org.dreamhorizon.pulseserver.dao.userapikey.models.UserApiKey;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyInfo;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.util.SecureRandomUtil;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserApiKeyService {

  private static final String KEY_PREFIX = "pulse_mcp_";
  private static final int RANDOM_LENGTH = 32;
  private static final int DISPLAY_PREFIX_LENGTH = 20;

  private final UserApiKeyDao userApiKeyDao;
  private final ProjectApiKeyEncryptionUtil encryptionUtil;

  public Single<UserApiKeyInfo> createApiKey(String userId, String displayName) {
    String rawKey = KEY_PREFIX + SecureRandomUtil.generateAlphanumeric(RANDOM_LENGTH);
    String hash = encryptionUtil.generateDigest(rawKey);
    String prefix = rawKey.substring(0, DISPLAY_PREFIX_LENGTH);

    return userApiKeyDao.createApiKey(userId, displayName, hash, prefix)
        .map(key -> UserApiKeyInfo.builder()
            .id(key.getId())
            .displayName(key.getDisplayName())
            .rawApiKey(rawKey)
            .keyPrefix(key.getKeyPrefix())
            .createdAt(key.getCreatedAt() != null ? key.getCreatedAt() : Instant.now())
            .build())
        .doOnSuccess(k -> log.info("Created user API key {} for user: {}", k.getId(), userId));
  }

  public Single<List<UserApiKeyPublicInfo>> listApiKeys(String userId) {
    return userApiKeyDao.findActiveByUser(userId)
        .map(keys -> keys.stream().map(this::toPublicInfo).toList())
        .doOnError(e -> log.error("Failed to list API keys for user: {}", userId, e));
  }

  public Completable revokeApiKey(Long keyId, String userId, String revokedBy) {
    return userApiKeyDao.revoke(keyId, userId, revokedBy);
  }

  public Maybe<String> validateAndGetUserId(String rawApiKey) {
    String hash = encryptionUtil.generateDigest(rawApiKey);
    return userApiKeyDao.findActiveByHash(hash)
        .map(UserApiKey::getUserId);
  }

  private UserApiKeyPublicInfo toPublicInfo(UserApiKey key) {
    return UserApiKeyPublicInfo.builder()
        .id(key.getId())
        .displayName(key.getDisplayName())
        .keyPrefix(key.getKeyPrefix())
        .isActive(key.getIsActive())
        .createdAt(key.getCreatedAt())
        .build();
  }
}
