package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;

public interface IS3BucketClient {
  Single<EmptyResponse> uploadObject(String bucketName, String objectKey, Object object);
}