package org.dreamhorizon.pulseserver.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.configs.ICloudFrontClient;
import software.amazon.awssdk.services.cloudfront.CloudFrontAsyncClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class CloudFrontClient implements ICloudFrontClient {

  private final CloudFrontAsyncClient cloudFrontAsyncClient;


  @Override
  public Single<EmptyResponse> invalidateCache(String distributionId, String asset) {
    List<String> paths = List.of(asset);

    InvalidationBatch batch = InvalidationBatch.builder()
        .paths(Paths.builder()
            .quantity(paths.size())
            .items(paths)
            .build())
        .callerReference(UUID.randomUUID().toString())
        .build();

    CompletableFuture<CreateInvalidationResponse> resp = cloudFrontAsyncClient
        .createInvalidation(builder -> builder
            .distributionId(distributionId)
            .invalidationBatch(batch));

    return Single.fromFuture(resp).flatMap(res -> Single.just(EmptyResponse.emptyResponse));
  }
}

