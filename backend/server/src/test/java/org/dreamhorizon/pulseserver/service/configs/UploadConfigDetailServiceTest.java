package org.dreamhorizon.pulseserver.service.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class UploadConfigDetailServiceTest {

  @Mock
  IS3BucketClient s3BucketClient;

  @Mock
  ICloudFrontClient cloudFrontClient;

  @Mock
  ApplicationConfig applicationConfig;

  @Mock
  ConfigService configService;

  UploadConfigDetailService uploadConfigDetailService;

  private static final String TEST_BUCKET_NAME = "test-config-bucket";
  private static final String TEST_FILE_PATH = "config/details.json";
  private static final String TEST_DISTRIBUTION_ID = "EABC123456789";
  private static final String TEST_ASSET_PATH = "/config/details.json";

  @BeforeEach
  void setUp() {
    uploadConfigDetailService = new UploadConfigDetailService(
        s3BucketClient,
        cloudFrontClient,
        applicationConfig,
        configService
    );

    // Setup default config values
    when(applicationConfig.getConfigS3BucketName()).thenReturn(TEST_BUCKET_NAME);
    when(applicationConfig.getConfigDetailsS3BucketFilePath()).thenReturn(TEST_FILE_PATH);
    when(applicationConfig.getConfigDetailCloudFrontDistributionId()).thenReturn(TEST_DISTRIBUTION_ID);
    when(applicationConfig.getConfigDetailCloudFrontAssetPath()).thenReturn(TEST_ASSET_PATH);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestPushInteractionDetailsToObjectStore {

    @Test
    void shouldUploadConfigAndInvalidateCacheSuccessfully() {
      // Given
      PulseConfig activeConfig = PulseConfig.builder()
          .version(1L)
          .description("Active Test Config")
          .build();

      when(configService.getActiveConfig()).thenReturn(Single.just(activeConfig));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), eq(activeConfig)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadConfigDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(configService).getActiveConfig();
      verify(s3BucketClient).uploadObject(TEST_BUCKET_NAME, TEST_FILE_PATH, activeConfig);
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
      verifyNoMoreInteractions(configService, s3BucketClient, cloudFrontClient);
    }

    @Test
    void shouldPropagateErrorWhenConfigServiceFails() {
      // Given
      RuntimeException configError = new RuntimeException("Failed to get active config");
      when(configService.getActiveConfig()).thenReturn(Single.error(configError));

      // When
      var testObserver = uploadConfigDetailService.pushInteractionDetailsToObjectStore().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Failed to get active config"));

      verify(configService).getActiveConfig();
      verify(s3BucketClient, never()).uploadObject(any(), any(), any());
      verify(cloudFrontClient, never()).invalidateCache(any(), any());
    }

    @Test
    void shouldPropagateErrorWhenS3UploadFails() {
      // Given
      PulseConfig activeConfig = PulseConfig.builder()
          .version(2L)
          .description("Test Config")
          .build();

      RuntimeException s3Error = new RuntimeException("S3 upload failed");

      when(configService.getActiveConfig()).thenReturn(Single.just(activeConfig));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), eq(activeConfig)))
          .thenReturn(Single.error(s3Error));

      // When
      var testObserver = uploadConfigDetailService.pushInteractionDetailsToObjectStore().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("S3 upload failed"));

      verify(configService).getActiveConfig();
      verify(s3BucketClient).uploadObject(TEST_BUCKET_NAME, TEST_FILE_PATH, activeConfig);
      verify(cloudFrontClient, never()).invalidateCache(any(), any());
    }

    @Test
    void shouldPropagateErrorWhenCloudFrontInvalidationFails() {
      // Given
      PulseConfig activeConfig = PulseConfig.builder()
          .version(3L)
          .description("Test Config")
          .build();

      RuntimeException cloudFrontError = new RuntimeException("CloudFront invalidation failed");

      when(configService.getActiveConfig()).thenReturn(Single.just(activeConfig));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), eq(activeConfig)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.error(cloudFrontError));

      // When
      var testObserver = uploadConfigDetailService.pushInteractionDetailsToObjectStore().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("CloudFront invalidation failed"));

      verify(configService).getActiveConfig();
      verify(s3BucketClient).uploadObject(TEST_BUCKET_NAME, TEST_FILE_PATH, activeConfig);
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
    }

    @Test
    void shouldUseCorrectConfigValues() {
      // Given
      String customBucket = "custom-bucket";
      String customFilePath = "custom/path.json";
      String customDistributionId = "ECUSTOM12345";
      String customAssetPath = "/custom/path.json";

      when(applicationConfig.getConfigS3BucketName()).thenReturn(customBucket);
      when(applicationConfig.getConfigDetailsS3BucketFilePath()).thenReturn(customFilePath);
      when(applicationConfig.getConfigDetailCloudFrontDistributionId()).thenReturn(customDistributionId);
      when(applicationConfig.getConfigDetailCloudFrontAssetPath()).thenReturn(customAssetPath);

      PulseConfig activeConfig = PulseConfig.builder()
          .version(4L)
          .description("Custom Config")
          .build();

      when(configService.getActiveConfig()).thenReturn(Single.just(activeConfig));
      when(s3BucketClient.uploadObject(eq(customBucket), eq(customFilePath), eq(activeConfig)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(customDistributionId), eq(customAssetPath)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadConfigDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(s3BucketClient).uploadObject(customBucket, customFilePath, activeConfig);
      verify(cloudFrontClient).invalidateCache(customDistributionId, customAssetPath);
    }

    @Test
    void shouldHandleNullConfigFieldsGracefully() {
      // Given
      PulseConfig minimalConfig = PulseConfig.builder()
          .version(5L)
          .build();

      when(configService.getActiveConfig()).thenReturn(Single.just(minimalConfig));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), eq(minimalConfig)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadConfigDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestConstants {

    @Test
    void shouldHaveCorrectTagSuccessConstant() {
      assertThat(UploadConfigDetailService.TAG_SUCCESS).isEqualTo("success");
    }

    @Test
    void shouldHaveCorrectTagErrorConstant() {
      assertThat(UploadConfigDetailService.TAG_ERROR).isEqualTo("error");
    }
  }
}

