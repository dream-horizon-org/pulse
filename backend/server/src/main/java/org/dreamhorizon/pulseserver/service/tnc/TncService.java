package org.dreamhorizon.pulseserver.service.tnc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.tnc.TncDao;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncAcceptance;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncVersion;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TncService {

  private static final String S3_KEY_FORMAT = "tnc/%s/%s.html";
  private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);

  private final TncDao tncDao;
  private final S3AsyncClient s3AsyncClient;
  private final S3Presigner s3Presigner;
  private final ApplicationConfig applicationConfig;

  /**
   * Get the current TnC status for a tenant.
   * Returns accepted=true if the tenant has accepted the current active version.
   */
  public Single<TncStatusResult> getTncStatus(String tenantId) {
    return tncDao.getActiveVersion()
        .switchIfEmpty(Single.error(new RuntimeException("No active TnC version configured")))
        .flatMap(version ->
            tncDao.getAcceptance(tenantId, version.getId())
                .map(acceptance -> TncStatusResult.builder()
                    .accepted(true)
                    .version(version)
                    .acceptedByEmail(acceptance.getAcceptedByEmail())
                    .acceptedAt(acceptance.getAcceptedAt())
                    .build())
                .switchIfEmpty(Single.just(TncStatusResult.builder()
                    .accepted(false)
                    .version(version)
                    .build()))
        );
  }

  /**
   * Check if a tenant has accepted the current active TnC version.
   * Returns true if accepted, if no TnC version is active, or if the check fails for any reason.
   * Designed to never break the login flow.
   */
  public Single<Boolean> hasTenantAcceptedTnc(String tenantId) {
    return tncDao.getActiveVersion()
        .flatMapSingle(version ->
            tncDao.getAcceptance(tenantId, version.getId())
                .map(acceptance -> true)
                .switchIfEmpty(Single.just(false))
        )
        .switchIfEmpty(Single.just(true))
        .onErrorReturnItem(true);
  }

  /**
   * Record TnC acceptance for a tenant.
   */
  public Single<TncAcceptance> acceptTnc(String tenantId, Long versionId, String email,
      String userAgent) {
    return tncDao.getActiveVersion()
        .switchIfEmpty(Single.error(new RuntimeException("No active TnC version configured")))
        .flatMap(activeVersion -> {
          if (!activeVersion.getId().equals(versionId)) {
            return Single.error(new IllegalArgumentException(
                "Version " + versionId + " is not the currently active version"));
          }
          return tncDao.insertAcceptance(tenantId, versionId, email, userAgent);
        });
  }

  /**
   * Upload all three TnC documents to S3 and publish a new version.
   * Deactivates any previous version and makes this one active.
   */
  public Single<TncVersion> uploadAndPublish(
      String version, String summary, String createdBy,
      byte[] tosBytes, byte[] aupBytes, byte[] ppBytes) {

    if (tosBytes == null || tosBytes.length == 0) {
      return Single.error(new IllegalArgumentException("Terms of Service document is required"));
    }
    if (aupBytes == null || aupBytes.length == 0) {
      return Single.error(new IllegalArgumentException("Acceptable Use Policy document is required"));
    }
    if (ppBytes == null || ppBytes.length == 0) {
      return Single.error(new IllegalArgumentException("Privacy Policy document is required"));
    }

    Single<String> uploadTos = uploadToS3(version, "tos", tosBytes);
    Single<String> uploadAup = uploadToS3(version, "aup", aupBytes);
    Single<String> uploadPp = uploadToS3(version, "privacy-policy", ppBytes);

    return Single.zip(uploadTos, uploadAup, uploadPp, (tosUrl, aupUrl, ppUrl) -> {
      log.info("All 3 TnC documents uploaded for version {}", version);
      return new String[]{tosUrl, aupUrl, ppUrl};
    }).flatMap(urls ->
        tncDao.publishVersion(version, urls[0], urls[1], urls[2], summary, createdBy)
    );
  }

  public Flowable<TncAcceptance> getAcceptanceHistory(String tenantId) {
    return tncDao.getAcceptanceHistory(tenantId);
  }

  /**
   * Convert an s3:// URL to a presigned HTTPS URL.
   */
  public String generatePresignedUrl(String s3Url) {
    if (s3Url == null || !s3Url.startsWith("s3://")) {
      return s3Url;
    }
    try {
      URI uri = URI.create(s3Url);
      String bucket = uri.getHost();
      String key = uri.getPath().substring(1);

      GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
          .signatureDuration(PRESIGN_DURATION)
          .getObjectRequest(GetObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .responseContentType("text/html")
              .build())
          .build();

      return s3Presigner.presignGetObject(presignRequest).url().toString();
    } catch (Exception e) {
      log.error("Failed to generate presigned URL for {}: {}", s3Url, e.getMessage());
      return s3Url;
    }
  }

  private Single<String> uploadToS3(String version, String docType, byte[] fileBytes) {
    String bucket = applicationConfig.getTncS3BucketName();
    String objectKey = String.format(S3_KEY_FORMAT, version, docType);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .contentType("text/html")
        .build();

    return Single.fromFuture(
        s3AsyncClient.putObject(putRequest, AsyncRequestBody.fromBytes(fileBytes))
    ).map(response -> {
      String s3Url = String.format("s3://%s/%s", bucket, objectKey);
      log.info("Uploaded TnC document to {}", s3Url);
      return s3Url;
    });
  }
}
