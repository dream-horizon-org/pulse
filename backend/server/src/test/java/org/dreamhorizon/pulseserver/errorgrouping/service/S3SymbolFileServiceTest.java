package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3SymbolFileServiceTest {

  @Mock
  private S3AsyncClient s3AsyncClient;

  @Mock
  private ApplicationConfig applicationConfig;

  private S3SymbolFileService s3SymbolFileService;

  @BeforeEach
  void setUp() {
    s3SymbolFileService = new S3SymbolFileService(s3AsyncClient, applicationConfig);
  }

  private UploadMetadata createMetadata() {
    return UploadMetadata.builder()
        .projectId("test-project")
        .appVersion("1.0.0")
        .versionCode("100")
        .platform("android")
        .type("mapping")
        .fileName("mapping.txt")
        .build();
  }

  @Nested
  class UploadFileTests {

    @Test
    void shouldUploadFileSuccessfully() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      PutObjectResponse mockResponse = PutObjectResponse.builder()
          .eTag("abc123")
          .build();
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      String s3Key = s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet();

      assertThat(s3Key).isEqualTo("symbols/test-project/android/mapping/1.0.0_100_mapping.txt");
      verify(s3AsyncClient).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    void shouldReturnErrorWhenBucketNotConfigured() {
      UploadMetadata metadata = createMetadata();
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn(null);

      assertThrows(IllegalStateException.class,
          () -> s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet());
    }

    @Test
    void shouldReturnErrorWhenBucketIsEmpty() {
      UploadMetadata metadata = createMetadata();
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("   ");

      assertThrows(IllegalStateException.class,
          () -> s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet());
    }

    @Test
    void shouldPropagateS3UploadError() {
      UploadMetadata metadata = createMetadata();
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      CompletableFuture<PutObjectResponse> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("S3 error"));
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(future);

      assertThrows(RuntimeException.class,
          () -> s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet());
    }

    @Test
    void shouldSanitizeS3KeyForSpecialCharacters() {
      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("test/project")
          .appVersion("1.0.0")
          .versionCode("100")
          .platform("android")
          .type("mapping")
          .fileName("mapping file.txt")
          .build();
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

      ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
      s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet();

      verify(s3AsyncClient).putObject(requestCaptor.capture(), any(AsyncRequestBody.class));
      assertThat(requestCaptor.getValue().key()).isEqualTo("symbols/test_project/android/mapping/1.0.0_100_mapping_file.txt");
    }

    @Test
    void shouldSetCorrectContentTypeForMapping() {
      UploadMetadata metadata = createMetadata();
      metadata.setType("mapping");
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

      ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
      s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet();

      verify(s3AsyncClient).putObject(requestCaptor.capture(), any(AsyncRequestBody.class));
      assertThat(requestCaptor.getValue().contentType()).isEqualTo("text/plain");
    }

    @Test
    void shouldSetCorrectContentTypeForJs() {
      UploadMetadata metadata = createMetadata();
      metadata.setType("js");
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

      ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
      s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet();

      verify(s3AsyncClient).putObject(requestCaptor.capture(), any(AsyncRequestBody.class));
      assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/json");
    }

    @Test
    void shouldSetCorrectContentTypeForDsym() {
      UploadMetadata metadata = createMetadata();
      metadata.setType("dsym");
      InputStream fileInputStream = new ByteArrayInputStream("content".getBytes());

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

      ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
      s3SymbolFileService.uploadFile(metadata, fileInputStream).blockingGet();

      verify(s3AsyncClient).putObject(requestCaptor.capture(), any(AsyncRequestBody.class));
      assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/zip");
    }
  }

  @Nested
  class DownloadFileAsBytesTests {

    @Test
    void shouldDownloadFileAsBytesSuccessfully() {
      String s3Key = "symbols/test-project/android/mapping/1.0.0_100_mapping.txt";
      byte[] content = "file content".getBytes(StandardCharsets.UTF_8);

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      GetObjectResponse mockResponse = GetObjectResponse.builder().build();
      ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(mockResponse, content);

      when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
          .thenReturn(CompletableFuture.completedFuture(responseBytes));

      byte[] result = s3SymbolFileService.downloadFileAsBytes(s3Key).blockingGet();

      assertThat(result).isEqualTo(content);
      verify(s3AsyncClient).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void shouldReturnErrorWhenBucketNotConfigured() {
      String s3Key = "symbols/test-key";

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn(null);

      assertThrows(IllegalStateException.class,
          () -> s3SymbolFileService.downloadFileAsBytes(s3Key).blockingGet());
    }

    @Test
    void shouldPropagateS3DownloadError() {
      String s3Key = "symbols/test-key";

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      CompletableFuture<ResponseBytes<GetObjectResponse>> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("S3 error"));
      when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
          .thenReturn(future);

      assertThrows(RuntimeException.class,
          () -> s3SymbolFileService.downloadFileAsBytes(s3Key).blockingGet());
    }
  }

  @Nested
  class DownloadFileAsStringTests {

    @Test
    void shouldDownloadFileAsStringSuccessfully() {
      String s3Key = "symbols/test-key";
      String content = "file content";
      byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      GetObjectResponse mockResponse = GetObjectResponse.builder().build();
      ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(mockResponse, contentBytes);

      when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
          .thenReturn(CompletableFuture.completedFuture(responseBytes));

      String result = s3SymbolFileService.downloadFileAsString(s3Key).blockingGet();

      assertThat(result).isEqualTo(content);
    }

    @Test
    void shouldHandleUtf8Encoding() {
      String s3Key = "symbols/test-key";
      String content = "测试内容 🚀";
      byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      GetObjectResponse mockResponse = GetObjectResponse.builder().build();
      ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(mockResponse, contentBytes);

      when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
          .thenReturn(CompletableFuture.completedFuture(responseBytes));

      String result = s3SymbolFileService.downloadFileAsString(s3Key).blockingGet();

      assertThat(result).isEqualTo(content);
    }
  }

  @Nested
  class DownloadFileAsInputStreamTests {

    @Test
    void shouldDownloadFileAsInputStreamSuccessfully() {
      String s3Key = "symbols/test-key";
      byte[] content = "file content".getBytes(StandardCharsets.UTF_8);

      when(applicationConfig.getSymbolFilesS3BucketName()).thenReturn("pulse-symbol-files");
      GetObjectResponse mockResponse = GetObjectResponse.builder().build();
      ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(mockResponse, content);

      when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
          .thenReturn(CompletableFuture.completedFuture(responseBytes));

      InputStream result = s3SymbolFileService.downloadFileAsInputStream(s3Key).blockingGet();

      assertThat(result).isNotNull();
      byte[] readBytes;
      try {
        readBytes = result.readAllBytes();
      } catch (java.io.IOException e) {
        throw new RuntimeException(e);
      }
      assertThat(readBytes).isEqualTo(content);
    }
  }
}
