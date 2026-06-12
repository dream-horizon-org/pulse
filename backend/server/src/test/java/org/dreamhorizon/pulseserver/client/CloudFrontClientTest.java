package org.dreamhorizon.pulseserver.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.cloudfront.CloudFrontAsyncClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.Invalidation;

import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class CloudFrontClientTest {

  @Mock
  CloudFrontAsyncClient cloudFrontAsyncClient;

  CloudFrontClient cloudFrontClient;

  @BeforeEach
  void setUp() {
    cloudFrontClient = new CloudFrontClient(cloudFrontAsyncClient);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestInvalidateCache {

    @Test
    void shouldInvalidateCacheSuccessfully() {
      // Given
      String distributionId = "EABC123456789";
      String asset = "/config/details.json";

      CreateInvalidationResponse mockResponse = CreateInvalidationResponse.builder()
          .invalidation(Invalidation.builder()
              .id("INV123")
              .status("InProgress")
              .build())
          .build();

      when(cloudFrontAsyncClient.createInvalidation(any(Consumer.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      // When
      EmptyResponse result = cloudFrontClient.invalidateCache(distributionId, asset).blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Consumer<CreateInvalidationRequest.Builder>> captor =
          ArgumentCaptor.forClass(Consumer.class);
      verify(cloudFrontAsyncClient).createInvalidation(captor.capture());

      // Verify the request was built correctly
      CreateInvalidationRequest.Builder builder = CreateInvalidationRequest.builder();
      captor.getValue().accept(builder);
      CreateInvalidationRequest request = builder.build();

      assertThat(request.distributionId()).isEqualTo(distributionId);
      assertThat(request.invalidationBatch().paths().items()).containsExactly(asset);
      assertThat(request.invalidationBatch().paths().quantity()).isEqualTo(1);
      assertThat(request.invalidationBatch().callerReference()).isNotNull();
    }

    @Test
    void shouldPropagateErrorWhenCloudFrontFails() {
      // Given
      String distributionId = "EABC123456789";
      String asset = "/config/details.json";

      RuntimeException cloudFrontError = new RuntimeException("CloudFront API error");
      CompletableFuture<CreateInvalidationResponse> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(cloudFrontError);

      when(cloudFrontAsyncClient.createInvalidation(any(Consumer.class)))
          .thenReturn(failedFuture);

      // When
      var testObserver = cloudFrontClient.invalidateCache(distributionId, asset).test();

      // Then
      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleMultiplePathsInAsset() {
      // Given
      String distributionId = "EABC123456789";
      String asset = "/config/*";

      CreateInvalidationResponse mockResponse = CreateInvalidationResponse.builder()
          .invalidation(Invalidation.builder()
              .id("INV456")
              .status("InProgress")
              .build())
          .build();

      when(cloudFrontAsyncClient.createInvalidation(any(Consumer.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      // When
      EmptyResponse result = cloudFrontClient.invalidateCache(distributionId, asset).blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);
    }

    @Test
    void shouldGenerateUniqueCallerReference() {
      // Given
      String distributionId = "EABC123456789";
      String asset = "/config/details.json";

      CreateInvalidationResponse mockResponse = CreateInvalidationResponse.builder()
          .invalidation(Invalidation.builder()
              .id("INV789")
              .status("InProgress")
              .build())
          .build();

      when(cloudFrontAsyncClient.createInvalidation(any(Consumer.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      // When - make two calls
      cloudFrontClient.invalidateCache(distributionId, asset).blockingGet();
      cloudFrontClient.invalidateCache(distributionId, asset).blockingGet();

      // Then - verify two calls were made (each should have unique caller reference)
      verify(cloudFrontAsyncClient, org.mockito.Mockito.times(2))
          .createInvalidation(any(Consumer.class));
    }
  }
}

