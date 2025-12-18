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
      PulseConfig config
  ) {
    return s3BucketClient
        .uploadObject(
            applicationConfig.getConfigS3BucketName(),
            applicationConfig.getConfigDetailsS3BucketFilePath(),
            config)
        .flatMap(resp -> cloudFrontClient
            .invalidateCache(
                applicationConfig.getConfigDetailCloudFrontDistributionId(),
                applicationConfig.getConfigDetailCloudFrontAssetPath()));
  }

  public Single<EmptyResponse> pushInteractionDetailsToObjectStore() {
    return configService
        .getActiveConfig()
        .flatMap(this::pushToObjectStoreAndInvalidateCache)
        .doOnError(this::handleUploadError)
        .doOnSuccess(res -> this.handleUploadSuccess());
  }
}