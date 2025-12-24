package org.dreamhorizon.pulseserver.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class S3BucketClient implements IS3BucketClient {

  private final S3AsyncClient client;
  private final ObjectMapper objectMapper;

  @Override
  public Single<EmptyResponse> uploadObject(String bucketName, String objectKey, Object object) {
    return Single.fromCallable(() -> objectMapper.writeValueAsBytes(object))
        .flatMap(bytes -> {
          CompletableFuture<?> future = client.putObject(
              (builder) -> builder.bucket(bucketName).key(objectKey),
              AsyncRequestBody.fromBytes(bytes));

          return Single.fromFuture(future);
        })
        .map(res -> EmptyResponse.emptyResponse);
  }

}
