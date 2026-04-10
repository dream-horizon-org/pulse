package org.dreamhorizon.pulseserver.service.interaction;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionConfig;
import org.dreamhorizon.pulseserver.service.configs.ICloudFrontClient;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;

@Slf4j
public class UploadInteractionDetailService {
  private static final UploadInteractionMapper mapper = UploadInteractionMapper.INSTANCE;

  private final IS3BucketClient s3BucketClient;
  private final ICloudFrontClient cloudFrontClient;
  private final ApplicationConfig applicationConfig;
  private final InteractionDao interactionDao;

  @Inject
  public UploadInteractionDetailService(
      IS3BucketClient s3BucketClient,
      ICloudFrontClient cloudFrontClient,
      ApplicationConfig applicationConfig,
      InteractionDao interactionDao
  ) {
    this.s3BucketClient = s3BucketClient;
    this.cloudFrontClient = cloudFrontClient;
    this.applicationConfig = applicationConfig;
    this.interactionDao = interactionDao;
  }

  private void handleUploadSuccess() {
    log.info("Interaction details uploaded to object store");
  }

  private void handleUploadError(Throwable error) {
    log.error("Error while uploading interaction details to object store", error);
  }

  private Single<EmptyResponse> pushToObjectStoreAndInvalidateCache(
      List<InteractionConfig> interactions,
      String projectId
  ) {
    String distributionId = applicationConfig.getCloudFrontDistributionId();
    String s3FilePath = getProjectAwarePath(projectId, applicationConfig.getInteractionDetailsS3BucketFilePath());
    String cloudFrontAssetPath = String.format("/%s",
        getProjectAwarePath(projectId, applicationConfig.getInteractionDetailCloudFrontAssetPath()));
    log.info("Uploading to S3 at path: {} for project: {}", s3FilePath, projectId);

    Single<EmptyResponse> uploadSingle = s3BucketClient
        .uploadObject(
            applicationConfig.getS3BucketName(),
            s3FilePath,
            interactions);

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
    return interactionDao
        .getAllActiveAndRunningInteractions(projectId)
        .map(this::toInteractionConfigs)
        .flatMap(res -> pushToObjectStoreAndInvalidateCache(res, projectId))
        .doOnError(this::handleUploadError)
        .doOnSuccess(res -> this.handleUploadSuccess());
  }

  private List<InteractionConfig> toInteractionConfigs(List<InteractionDetails> details) {
    return mapper.toInteractionConfig(details);
  }
}
