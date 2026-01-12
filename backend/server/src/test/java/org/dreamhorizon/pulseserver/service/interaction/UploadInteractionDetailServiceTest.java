package org.dreamhorizon.pulseserver.service.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.configs.ICloudFrontClient;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class UploadInteractionDetailServiceTest {

  @Mock
  IS3BucketClient s3BucketClient;

  @Mock
  ICloudFrontClient cloudFrontClient;

  @Mock
  ApplicationConfig applicationConfig;

  @Mock
  InteractionDao interactionDao;

  UploadInteractionDetailService uploadInteractionDetailService;

  private static final String TEST_BUCKET_NAME = "test-interaction-bucket";
  private static final String TEST_FILE_PATH = "config/interaction-config.json";
  private static final String TEST_DISTRIBUTION_ID = "EABC123456789";
  private static final String TEST_ASSET_PATH = "/config/interaction-config.json";

  @BeforeEach
  void setUp() {
    uploadInteractionDetailService = new UploadInteractionDetailService(
        s3BucketClient,
        cloudFrontClient,
        applicationConfig,
        interactionDao
    );

    // Setup default config values
    when(applicationConfig.getS3BucketName()).thenReturn(TEST_BUCKET_NAME);
    when(applicationConfig.getInteractionDetailsS3BucketFilePath()).thenReturn(TEST_FILE_PATH);
    when(applicationConfig.getCloudFrontDistributionId()).thenReturn(TEST_DISTRIBUTION_ID);
    when(applicationConfig.getInteractionDetailCloudFrontAssetPath()).thenReturn(TEST_ASSET_PATH);
  }

  private InteractionDetails createTestInteractionDetails(String name) {
    return InteractionDetails.builder()
        .id(1L)
        .name(name)
        .description("Test interaction")
        .status(InteractionStatus.RUNNING)
        .thresholdInMs(1000)
        .uptimeLowerLimitInMs(100)
        .uptimeMidLimitInMs(500)
        .uptimeUpperLimitInMs(1000)
        .events(List.of(
            Event.builder().name("TestEvent").build()
        ))
        .globalBlacklistedEvents(List.of())
        .createdBy("test@example.com")
        .updatedBy("test@example.com")
        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
        .build();
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestPushInteractionDetailsToObjectStore {

    @Test
    void shouldUploadInteractionDetailsAndInvalidateCacheSuccessfully() {
      // Given
      List<InteractionDetails> interactions = List.of(
          createTestInteractionDetails("Interaction1"),
          createTestInteractionDetails("Interaction2")
      );

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(interactions));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any()))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadInteractionDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient).uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any());
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
      verifyNoMoreInteractions(interactionDao, s3BucketClient, cloudFrontClient);
    }

    @Test
    void shouldHandleEmptyInteractionListSuccessfully() {
      // Given
      List<InteractionDetails> emptyInteractions = List.of();

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(emptyInteractions));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any()))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadInteractionDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient).uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any());
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
    }

    @Test
    void shouldPropagateErrorWhenInteractionDaoFails() {
      // Given
      RuntimeException daoError = new RuntimeException("Failed to get interactions");
      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.error(daoError));

      // When
      var testObserver = uploadInteractionDetailService.pushInteractionDetailsToObjectStore().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Failed to get interactions"));

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient, never()).uploadObject(any(), any(), any());
      verify(cloudFrontClient, never()).invalidateCache(any(), any());
    }

    @Test
    void shouldPropagateErrorWhenS3UploadFails() {
      // Given
      List<InteractionDetails> interactions = List.of(
          createTestInteractionDetails("Interaction1")
      );

      RuntimeException s3Error = new RuntimeException("S3 upload failed");

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(interactions));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any()))
          .thenReturn(Single.error(s3Error));

      // When
      var testObserver = uploadInteractionDetailService.pushInteractionDetailsToObjectStore().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("S3 upload failed"));

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient).uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any());
      verify(cloudFrontClient, never()).invalidateCache(any(), any());
    }

    @Test
    void shouldPropagateErrorWhenCloudFrontInvalidationFails() {
      // Given
      List<InteractionDetails> interactions = List.of(
          createTestInteractionDetails("Interaction1")
      );

      RuntimeException cloudFrontError = new RuntimeException("CloudFront invalidation failed");

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(interactions));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any()))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.error(cloudFrontError));

      // When
      var testObserver = uploadInteractionDetailService.pushInteractionDetailsToObjectStore().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("CloudFront invalidation failed"));

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient).uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any());
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
    }

    @Test
    void shouldUseCorrectConfigValues() {
      // Given
      String customBucket = "custom-interaction-bucket";
      String customFilePath = "custom/interaction-path.json";
      String customDistributionId = "ECUSTOM12345";
      String customAssetPath = "/custom/interaction-path.json";

      when(applicationConfig.getS3BucketName()).thenReturn(customBucket);
      when(applicationConfig.getInteractionDetailsS3BucketFilePath()).thenReturn(customFilePath);
      when(applicationConfig.getCloudFrontDistributionId()).thenReturn(customDistributionId);
      when(applicationConfig.getInteractionDetailCloudFrontAssetPath()).thenReturn(customAssetPath);

      List<InteractionDetails> interactions = List.of(
          createTestInteractionDetails("CustomInteraction")
      );

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(interactions));
      when(s3BucketClient.uploadObject(eq(customBucket), eq(customFilePath), any()))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(customDistributionId), eq(customAssetPath)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadInteractionDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(s3BucketClient).uploadObject(eq(customBucket), eq(customFilePath), any());
      verify(cloudFrontClient).invalidateCache(customDistributionId, customAssetPath);
    }

    @Test
    void shouldHandleSingleInteractionSuccessfully() {
      // Given
      List<InteractionDetails> interactions = List.of(
          createTestInteractionDetails("SingleInteraction")
      );

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(interactions));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any()))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadInteractionDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient).uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any());
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
    }

    @Test
    void shouldHandleMultipleInteractionsSuccessfully() {
      // Given
      List<InteractionDetails> interactions = List.of(
          createTestInteractionDetails("Interaction1"),
          createTestInteractionDetails("Interaction2"),
          createTestInteractionDetails("Interaction3"),
          createTestInteractionDetails("Interaction4"),
          createTestInteractionDetails("Interaction5")
      );

      when(interactionDao.getAllActiveAndRunningInteractions())
          .thenReturn(Single.just(interactions));
      when(s3BucketClient.uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any()))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));
      when(cloudFrontClient.invalidateCache(eq(TEST_DISTRIBUTION_ID), eq(TEST_ASSET_PATH)))
          .thenReturn(Single.just(EmptyResponse.emptyResponse));

      // When
      EmptyResponse result = uploadInteractionDetailService.pushInteractionDetailsToObjectStore()
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(interactionDao).getAllActiveAndRunningInteractions();
      verify(s3BucketClient).uploadObject(eq(TEST_BUCKET_NAME), eq(TEST_FILE_PATH), any());
      verify(cloudFrontClient).invalidateCache(TEST_DISTRIBUTION_ID, TEST_ASSET_PATH);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestConstructor {

    @Test
    void shouldCreateInstanceWithAllDependencies() {
      // Given & When
      UploadInteractionDetailService service = new UploadInteractionDetailService(
          s3BucketClient,
          cloudFrontClient,
          applicationConfig,
          interactionDao
      );

      // Then
      assertThat(service).isNotNull();
    }
  }
}

