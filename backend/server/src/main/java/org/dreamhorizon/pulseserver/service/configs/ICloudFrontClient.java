package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;

public interface ICloudFrontClient {
  Single<EmptyResponse> invalidateCache(String distributionId, String asset);
}