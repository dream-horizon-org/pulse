package org.dreamhorizon.pulseserver.service.configs;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;

@Slf4j
public class UploadConfigDetailService {
  public static final String TAG_SUCCESS = "success";
  public static final String TAG_ERROR = "error";

  private final IS3BucketClient s3BucketClient;
  private final ICloudFrontClient cloudFrontClient;
  private final ApplicationConfig applicationConfig;
  private final ConfigService configService;


  @Inject
  public UploadConfigDetailService(
      IS3BucketClient s3BucketClient,
      ICloudFrontClient cloudFrontClient,
      ApplicationConfig applicationConfig,
      ConfigService configService
  ) {
    this.s3BucketClient = s3BucketClient;
    this.cloudFrontClient = cloudFrontClient;
    this.applicationConfig = applicationConfig;
    this.configService = configService;
  }

  private void handleUploadSuccess() {
    log.info("Interaction details uploaded to object store");
  }

  private void handleUploadError(Throwable error) {
    log.error("Error while uploading interaction details to object store", error);
  }

  private Single<EmptyResponse> pushToObjectStoreAndInvalidateCache(
      PulseConfig config,
      String projectId
  ) {
    String distributionId = applicationConfig.getCloudFrontDistributionId();
    String s3FilePath = getProjectAwarePath(projectId, applicationConfig.getConfigDetailsS3BucketFilePath());
    String cloudFrontAssetPath = String.format("/%s",
        getProjectAwarePath(projectId, applicationConfig.getConfigDetailCloudFrontAssetPath()));

    log.info("Uploading to S3 at path: {} for project: {}", s3FilePath, projectId);
    Single<EmptyResponse> uploadSingle = s3BucketClient
        .uploadObject(
            applicationConfig.getS3BucketName(),
            s3FilePath,
            config);

    return uploadSingle
        .flatMap(resp -> {
          log.info("S3 upload successful for project: {}, invalidating CloudFront cache for distribution: {}",
              projectId, distributionId);
          return cloudFrontClient
              .invalidateCache(
                  distributionId,
                  cloudFrontAssetPath);
        });
  }

  /**
   * Constructs a pure project-based path.
   * Format: config/projects/{projectId}/{basePath}
   */
  private String getProjectAwarePath(String projectId, String basePath) {
    return String.format("config/projects/%s/%s", projectId, basePath);
  }

  public Single<EmptyResponse> pushInteractionDetailsToObjectStore(String projectId) {
    return configService
        .getActiveSdkConfig(projectId)
        .flatMap(config -> pushToObjectStoreAndInvalidateCache(config, projectId))
        .doOnError(this::handleUploadError)
        .doOnSuccess(res -> this.handleUploadSuccess());
  }
}