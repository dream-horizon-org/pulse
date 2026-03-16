package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class S3SymbolFileService {
  private static final String S3_KEY_FORMAT = "symbols/%s/%s/%s/%s/%s/%s";

  private final S3AsyncClient s3AsyncClient;
  private final ApplicationConfig applicationConfig;

  public Single<String> uploadFile(UploadMetadata metadata, InputStream fileInputStream) {
    String bucketName = applicationConfig.getSymbolFilesS3BucketName();
    if (bucketName == null || bucketName.trim().isEmpty()) {
      log.error("S3 bucket not configured");
      return Single.error(new IllegalStateException("S3 bucket name not configured"));
    }

    String s3Key = buildS3Key(metadata);
  
    return Single.fromCallable(() -> fileInputStream.readAllBytes())
    .flatMap(fileBytes -> {
      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(s3Key)
          .contentType(getContentType(metadata.getType()))
          .build();

      return Single.fromFuture(
          s3AsyncClient.putObject(putRequest, AsyncRequestBody.fromBytes(fileBytes))
      ).map(response -> {
        log.info("Uploaded to S3: key={}, size={} bytes", s3Key, fileBytes.length);
        return s3Key;
      });
    })
    .onErrorResumeNext(error -> {
      log.error("S3 upload failed: key={}, error={}", s3Key, error.getMessage(), error);
      return Single.error(error);
    });
  }

  public Single<byte[]> downloadFileAsBytes(String s3Key) {
    String bucketName = applicationConfig.getSymbolFilesS3BucketName();
    if (bucketName == null || bucketName.trim().isEmpty()) {
      log.error("S3 bucket not configured");
      return Single.error(new IllegalStateException("S3 bucket name not configured"));
    }

    GetObjectRequest getRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .build();

    return Single.fromFuture(
        s3AsyncClient.getObject(getRequest, AsyncResponseTransformer.toBytes())
    ).map(response -> {
      byte[] bytes = response.asByteArray();  // Extract bytes from S3 response
      log.info("Downloaded from S3: key={}, size={} bytes", s3Key, bytes.length);
      return bytes;
    })
    .onErrorResumeNext(error -> {
      log.error("S3 download failed: key={}, error={}", s3Key, error.getMessage(), error);
      return Single.error(error);
    });
  }

  public Single<String> downloadFileAsString(String s3Key) {
    return downloadFileAsBytes(s3Key)
        .map(bytes -> new String(bytes, StandardCharsets.UTF_8));  // Convert bytes to UTF-8 string
  }

  public Single<InputStream> downloadFileAsInputStream(String s3Key) {
    return downloadFileAsBytes(s3Key)
        .map(ByteArrayInputStream::new);  // Wrap bytes in InputStream
  }

  private String buildS3Key(UploadMetadata metadata) {
    String platform = sanitizeForS3Key(metadata.getPlatform());
    String projectId = sanitizeForS3Key(metadata.getProjectId());
    String appVersion = sanitizeForS3Key(metadata.getAppVersion());
    String versionCode = sanitizeForS3Key(metadata.getVersionCode());
    String framework = sanitizeForS3Key(metadata.getType());
    String fileName = sanitizeForS3Key(metadata.getFileName());

    return String.format(S3_KEY_FORMAT, platform, projectId, appVersion, versionCode, framework, fileName);
  }

  private String sanitizeForS3Key(String value) {
    if (value == null) {
      return "unknown";
    }
    return value.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private String getContentType(String fileType) {
    if (fileType == null) {
      return "application/octet-stream";
    }
    return switch (fileType.toLowerCase()) {
      case "js" -> "application/json";
      case "mapping" -> "text/plain";
      case "dsym" -> "application/zip";
      default -> "application/octet-stream";
    };
  }

}
