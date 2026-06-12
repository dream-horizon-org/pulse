package org.dreamhorizon.pulseserver.service.tnc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.tnc.TncDao;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncAcceptance;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TncServiceTest {

  @Mock TncDao tncDao;
  @Mock S3AsyncClient s3AsyncClient;
  @Mock S3Presigner s3Presigner;
  @Mock ApplicationConfig applicationConfig;

  TncService tncService;

  @BeforeEach
  void setup() {
    tncService = new TncService(tncDao, s3AsyncClient, s3Presigner, applicationConfig);
  }

  private TncVersion createTncVersion(long id, String version) {
    return TncVersion.builder()
        .id(id)
        .version(version)
        .tosS3Url("s3://bucket/tnc/" + version + "/tos.html")
        .aupS3Url("s3://bucket/tnc/" + version + "/aup.html")
        .privacyPolicyS3Url("s3://bucket/tnc/" + version + "/privacy-policy.html")
        .summary("Test terms")
        .active(true)
        .createdBy("admin@example.com")
        .build();
  }

  private TncAcceptance createTncAcceptance(String tenantId, long versionId, String email) {
    return TncAcceptance.builder()
        .id(1L)
        .tenantId(tenantId)
        .tncVersionId(versionId)
        .acceptedByEmail(email)
        .acceptedAt("2024-01-15 10:00:00")
        .userAgent("Mozilla/5.0")
        .build();
  }

  @Nested
  class GetTncStatus {

    @Test
    void shouldReturnAcceptedStatusWhenTenantHasAccepted() {
      TncVersion version = createTncVersion(1L, "2024-01");
      TncAcceptance acceptance = createTncAcceptance("tenant-1", 1L, "user@example.com");

      when(tncDao.getActiveVersion()).thenReturn(Maybe.just(version));
      when(tncDao.getAcceptance("tenant-1", 1L)).thenReturn(Maybe.just(acceptance));

      TncStatusResult result = tncService.getTncStatus("tenant-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.isAccepted()).isTrue();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getAcceptedByEmail()).isEqualTo("user@example.com");
      assertThat(result.getAcceptedAt()).isEqualTo("2024-01-15 10:00:00");
    }

    @Test
    void shouldReturnNotAcceptedStatusWhenTenantHasNotAccepted() {
      TncVersion version = createTncVersion(1L, "2024-01");

      when(tncDao.getActiveVersion()).thenReturn(Maybe.just(version));
      when(tncDao.getAcceptance("tenant-1", 1L)).thenReturn(Maybe.empty());

      TncStatusResult result = tncService.getTncStatus("tenant-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.isAccepted()).isFalse();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getAcceptedByEmail()).isNull();
      assertThat(result.getAcceptedAt()).isNull();
    }

    @Test
    void shouldThrowWhenNoActiveVersion() {
      when(tncDao.getActiveVersion()).thenReturn(Maybe.empty());

      assertThatThrownBy(() -> tncService.getTncStatus("tenant-1").blockingGet())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("No active TnC version configured");
    }
  }

  @Nested
  class HasTenantAcceptedTnc {

    @Test
    void shouldReturnTrueWhenTenantHasAccepted() {
      TncVersion version = createTncVersion(1L, "2024-01");
      TncAcceptance acceptance = createTncAcceptance("tenant-1", 1L, "user@example.com");

      when(tncDao.getActiveVersion()).thenReturn(Maybe.just(version));
      when(tncDao.getAcceptance("tenant-1", 1L)).thenReturn(Maybe.just(acceptance));

      Boolean result = tncService.hasTenantAcceptedTnc("tenant-1").blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTenantHasNotAccepted() {
      TncVersion version = createTncVersion(1L, "2024-01");

      when(tncDao.getActiveVersion()).thenReturn(Maybe.just(version));
      when(tncDao.getAcceptance("tenant-1", 1L)).thenReturn(Maybe.empty());

      Boolean result = tncService.hasTenantAcceptedTnc("tenant-1").blockingGet();

      assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenNoActiveVersion() {
      when(tncDao.getActiveVersion()).thenReturn(Maybe.empty());

      Boolean result = tncService.hasTenantAcceptedTnc("tenant-1").blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnTrueOnError() {
      when(tncDao.getActiveVersion()).thenReturn(Maybe.error(new RuntimeException("DB error")));

      Boolean result = tncService.hasTenantAcceptedTnc("tenant-1").blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class AcceptTnc {

    @Test
    void shouldAcceptTncSuccessfully() {
      TncVersion version = createTncVersion(1L, "2024-01");
      TncAcceptance acceptance = createTncAcceptance("tenant-1", 1L, "user@example.com");

      when(tncDao.getActiveVersion()).thenReturn(Maybe.just(version));
      when(tncDao.insertAcceptance("tenant-1", 1L, "user@example.com", "Mozilla/5.0"))
          .thenReturn(Single.just(acceptance));

      TncAcceptance result = tncService.acceptTnc("tenant-1", 1L, "user@example.com", "Mozilla/5.0")
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getAcceptedByEmail()).isEqualTo("user@example.com");
    }

    @Test
    void shouldThrowWhenVersionMismatch() {
      TncVersion version = createTncVersion(1L, "2024-01");

      when(tncDao.getActiveVersion()).thenReturn(Maybe.just(version));

      assertThatThrownBy(() -> tncService.acceptTnc("tenant-1", 999L, "user@example.com", "UA")
          .blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("999")
          .hasMessageContaining("not the currently active version");
    }

    @Test
    void shouldThrowWhenNoActiveVersion() {
      when(tncDao.getActiveVersion()).thenReturn(Maybe.empty());

      assertThatThrownBy(() -> tncService.acceptTnc("tenant-1", 1L, "user@example.com", "UA")
          .blockingGet())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("No active TnC version configured");
    }
  }

  @Nested
  class UploadAndPublish {

    @Test
    void shouldUploadAndPublishSuccessfully() {
      TncVersion version = createTncVersion(1L, "2024-02");
      byte[] tosBytes = "<html>ToS</html>".getBytes();
      byte[] aupBytes = "<html>AUP</html>".getBytes();
      byte[] ppBytes = "<html>PP</html>".getBytes();

      when(applicationConfig.getTncS3BucketName()).thenReturn("tnc-bucket");
      when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
          .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));
      when(tncDao.publishVersion(anyString(), anyString(), anyString(), anyString(),
          anyString(), anyString()))
          .thenReturn(Single.just(version));

      TncVersion result = tncService.uploadAndPublish(
          "2024-02",
          "Updated terms",
          "admin@example.com",
          tosBytes,
          aupBytes,
          ppBytes
      ).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo("2024-02");
    }

    @Test
    void shouldThrowWhenTosBytesIsNull() {
      assertThatThrownBy(() -> tncService.uploadAndPublish(
          "v1",
          "sum",
          "admin",
          null,
          new byte[]{1},
          new byte[]{1}
      ).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Terms of Service");
    }

    @Test
    void shouldThrowWhenTosBytesIsEmpty() {
      assertThatThrownBy(() -> tncService.uploadAndPublish(
          "v1",
          "sum",
          "admin",
          new byte[]{},
          new byte[]{1},
          new byte[]{1}
      ).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Terms of Service");
    }

    @Test
    void shouldThrowWhenAupBytesIsNull() {
      assertThatThrownBy(() -> tncService.uploadAndPublish(
          "v1",
          "sum",
          "admin",
          new byte[]{1},
          null,
          new byte[]{1}
      ).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Acceptable Use Policy");
    }

    @Test
    void shouldThrowWhenPpBytesIsNull() {
      assertThatThrownBy(() -> tncService.uploadAndPublish(
          "v1",
          "sum",
          "admin",
          new byte[]{1},
          new byte[]{1},
          null
      ).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Privacy Policy");
    }
  }

  @Nested
  class GetAcceptanceHistory {

    @Test
    void shouldDelegateToDao() {
      TncAcceptance acceptance = createTncAcceptance("tenant-1", 1L, "user@example.com");
      when(tncDao.getAcceptanceHistory("tenant-1"))
          .thenReturn(Flowable.just(acceptance));

      List<TncAcceptance> result = tncService.getAcceptanceHistory("tenant-1")
          .toList().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0)).isEqualTo(acceptance);
    }

    @Test
    void shouldReturnEmptyWhenNoHistory() {
      when(tncDao.getAcceptanceHistory("tenant-1")).thenReturn(Flowable.empty());

      List<TncAcceptance> result = tncService.getAcceptanceHistory("tenant-1")
          .toList().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GeneratePresignedUrl {

    @Test
    void shouldGeneratePresignedUrlForValidS3Url() throws MalformedURLException {
      PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
      when(presignedRequest.url()).thenReturn(new URL("https://presigned.example.com/doc?signature=xxx"));
      when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
          .thenReturn(presignedRequest);

      String result = tncService.generatePresignedUrl("s3://my-bucket/tnc/v1/tos.html");

      assertThat(result).isEqualTo("https://presigned.example.com/doc?signature=xxx");
    }

    @Test
    void shouldReturnNullWhenUrlIsNull() {
      String result = tncService.generatePresignedUrl(null);

      assertThat(result).isNull();
    }

    @Test
    void shouldReturnOriginalWhenNotS3Url() {
      String nonS3Url = "https://example.com/doc.html";

      String result = tncService.generatePresignedUrl(nonS3Url);

      assertThat(result).isEqualTo(nonS3Url);
    }

    @Test
    void shouldReturnOriginalUrlWhenPresigningFails() {
      when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
          .thenThrow(new RuntimeException("Presign failed"));

      String result = tncService.generatePresignedUrl("s3://bucket/key.html");

      assertThat(result).isEqualTo("s3://bucket/key.html");
    }
  }
}
