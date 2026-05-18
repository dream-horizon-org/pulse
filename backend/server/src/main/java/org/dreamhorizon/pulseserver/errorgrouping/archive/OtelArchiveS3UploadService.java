package org.dreamhorizon.pulseserver.errorgrouping.archive;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Slf4j
@Singleton
public class OtelArchiveS3UploadService {

  private final S3AsyncClient s3Client;

  @Inject
  public OtelArchiveS3UploadService(StackTraceArchiveConfig config) {
    this.s3Client = buildClient(config);
  }

  private static S3AsyncClient buildClient(StackTraceArchiveConfig config) {
    AwsCredentialsProvider creds = resolveCredentials();
    S3AsyncClientBuilder builder = S3AsyncClient.builder()
        .region(Region.of(config.getS3Region()))
        .credentialsProvider(creds);
    String endpoint = config.getS3Endpoint();
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint.trim())).forcePathStyle(true);
    }
    return builder.build();
  }

  private static AwsCredentialsProvider resolveCredentials() {
    String profile = System.getenv("AWS_PROFILE");
    if (profile != null && !profile.isBlank()) {
      return ProfileCredentialsProvider.create(profile);
    }
    return DefaultCredentialsProvider.create();
  }

  public CompletableFuture<PutObjectResponse> upload(String bucket, String key, File file) {
    Objects.requireNonNull(bucket, "bucket");
    log.debug("[StackTraceArchive] Uploading s3://{}/{} size={}", bucket, key, file.length());
    PutObjectRequest req = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build();
    return s3Client.putObject(req, AsyncRequestBody.fromFile(file))
        .whenComplete((resp, err) -> {
          if (err != null) {
            log.error("[StackTraceArchive] Failed s3://{}/{}: {}", bucket, key, err.getMessage());
          } else {
            log.info("[StackTraceArchive] OK s3://{}/{} etag={}", bucket, key, resp.eTag());
          }
        });
  }
}
