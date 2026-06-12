package org.dreamhorizon.pulseserver.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3BucketClientTest {

  @Mock
  S3AsyncClient s3AsyncClient;

  @Mock
  ObjectMapper objectMapper;

  S3BucketClient s3BucketClient;

  @BeforeEach
  void setUp() {
    s3BucketClient = new S3BucketClient(s3AsyncClient, objectMapper);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestUploadObject {

    @Test
    void shouldUploadObjectSuccessfully() throws JsonProcessingException {
      // Given
      String bucketName = "my-config-bucket";
      String objectKey = "config/details.json";
      Map<String, String> testObject = Map.of("key", "value");
      byte[] serializedBytes = "{\"key\":\"value\"}".getBytes();

      when(objectMapper.writeValueAsBytes(testObject)).thenReturn(serializedBytes);

      PutObjectResponse mockResponse = PutObjectResponse.builder()
          .eTag("abc123")
          .build();

      when(s3AsyncClient.putObject(any(Consumer.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      // When
      EmptyResponse result = s3BucketClient.uploadObject(bucketName, objectKey, testObject)
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);

      verify(objectMapper).writeValueAsBytes(testObject);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Consumer<PutObjectRequest.Builder>> requestCaptor =
          ArgumentCaptor.forClass(Consumer.class);
      ArgumentCaptor<AsyncRequestBody> bodyCaptor =
          ArgumentCaptor.forClass(AsyncRequestBody.class);

      verify(s3AsyncClient).putObject(requestCaptor.capture(), bodyCaptor.capture());

      // Verify the request was built correctly
      PutObjectRequest.Builder builder = PutObjectRequest.builder();
      requestCaptor.getValue().accept(builder);
      PutObjectRequest request = builder.build();

      assertThat(request.bucket()).isEqualTo(bucketName);
      assertThat(request.key()).isEqualTo(objectKey);
    }

    @Test
    void shouldPropagateErrorWhenSerializationFails() throws JsonProcessingException {
      // Given
      String bucketName = "my-config-bucket";
      String objectKey = "config/details.json";
      Object testObject = new Object();

      JsonProcessingException serializationError =
          new JsonProcessingException("Serialization failed") {};

      when(objectMapper.writeValueAsBytes(testObject)).thenThrow(serializationError);

      // When
      var testObserver = s3BucketClient.uploadObject(bucketName, objectKey, testObject).test();

      // Then
      testObserver.assertError(JsonProcessingException.class);
    }

    @Test
    void shouldPropagateErrorWhenS3UploadFails() throws JsonProcessingException {
      // Given
      String bucketName = "my-config-bucket";
      String objectKey = "config/details.json";
      Map<String, String> testObject = Map.of("key", "value");
      byte[] serializedBytes = "{\"key\":\"value\"}".getBytes();

      when(objectMapper.writeValueAsBytes(testObject)).thenReturn(serializedBytes);

      RuntimeException s3Error = new RuntimeException("S3 upload failed");
      CompletableFuture<PutObjectResponse> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(s3Error);

      when(s3AsyncClient.putObject(any(Consumer.class), any(AsyncRequestBody.class)))
          .thenReturn(failedFuture);

      // When
      var testObserver = s3BucketClient.uploadObject(bucketName, objectKey, testObject).test();

      // Then
      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleComplexObjectSerialization() throws JsonProcessingException {
      // Given
      String bucketName = "my-config-bucket";
      String objectKey = "config/complex.json";

      // Create a complex nested object
      Map<String, Object> complexObject = Map.of(
          "name", "TestConfig",
          "version", 1,
          "nested", Map.of("inner", "value")
      );

      byte[] serializedBytes = "{\"name\":\"TestConfig\",\"version\":1,\"nested\":{\"inner\":\"value\"}}".getBytes();

      when(objectMapper.writeValueAsBytes(complexObject)).thenReturn(serializedBytes);

      PutObjectResponse mockResponse = PutObjectResponse.builder()
          .eTag("xyz789")
          .build();

      when(s3AsyncClient.putObject(any(Consumer.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      // When
      EmptyResponse result = s3BucketClient.uploadObject(bucketName, objectKey, complexObject)
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);
      verify(objectMapper).writeValueAsBytes(complexObject);
    }

    @Test
    void shouldHandleEmptyObject() throws JsonProcessingException {
      // Given
      String bucketName = "my-config-bucket";
      String objectKey = "config/empty.json";
      Map<String, String> emptyObject = Map.of();
      byte[] serializedBytes = "{}".getBytes();

      when(objectMapper.writeValueAsBytes(emptyObject)).thenReturn(serializedBytes);

      PutObjectResponse mockResponse = PutObjectResponse.builder()
          .eTag("empty123")
          .build();

      when(s3AsyncClient.putObject(any(Consumer.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      // When
      EmptyResponse result = s3BucketClient.uploadObject(bucketName, objectKey, emptyObject)
          .blockingGet();

      // Then
      assertThat(result).isEqualTo(EmptyResponse.emptyResponse);
    }
  }
}

