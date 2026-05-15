package org.dreamhorizon.pulses3archiver.service;

import com.google.inject.Inject;
import java.io.File;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulses3archiver.config.S3Config;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class S3UploadService {

  private final S3AsyncClient s3Client;
  private final String bucket;

  @Inject
  public S3UploadService(S3Config config) {
    this.bucket = config.getBucket();
    S3AsyncClientBuilder builder = S3AsyncClient.builder()
        .region(Region.of(config.getRegion()));

    // Support local S3-compatible (e.g. LocalStack) via S3_ENDPOINT env
    String endpoint = System.getenv("S3_ENDPOINT");
    if (endpoint != null && !endpoint.isEmpty()) {
      builder.endpointOverride(URI.create(endpoint))
             .forcePathStyle(true);
    }

    this.s3Client = builder.build();
  }

  public CompletableFuture<PutObjectResponse> upload(String key, File file) {
    log.debug("[S3Upload] Uploading s3://{}/{} size={}", bucket, key, file.length());
    PutObjectRequest req = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build();
    return s3Client.putObject(req, AsyncRequestBody.fromFile(file))
        .whenComplete((resp, err) -> {
          if (err != null) {
            log.error("[S3Upload] Failed s3://{}/{}: {}", bucket, key, err.getMessage());
          } else {
            log.info("[S3Upload] OK s3://{}/{} etag={}", bucket, key, resp.eTag());
          }
        });
  }

  public void close() {
    s3Client.close();
  }
}
