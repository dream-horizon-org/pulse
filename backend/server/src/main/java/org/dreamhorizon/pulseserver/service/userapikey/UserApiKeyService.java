package org.dreamhorizon.pulseserver.service.userapikey;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyInfo;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyPublicInfo;

public interface UserApiKeyService {

  Single<UserApiKeyInfo> createApiKey(String userId, String displayName);

  Single<List<UserApiKeyPublicInfo>> listApiKeys(String userId);

  Completable revokeApiKey(Long keyId, String userId, String revokedBy);

  Maybe<String> validateAndGetUserId(String rawApiKey);
}
