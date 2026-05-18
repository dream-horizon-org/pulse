package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.reactivex.rxjava3.core.Completable;
import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class StackTraceArchiveServiceTest {

  private static final DateTimeFormatter CH_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS").withZone(ZoneOffset.UTC);

  @Mock
  private OtelArchiveS3UploadService s3UploadService;

  @TempDir
  java.nio.file.Path tempDir;

  private StackTraceEvent sampleEvent() {
    Instant ts = Instant.parse("2026-05-18T12:00:00Z");
    return StackTraceEvent.builder()
        .timestamp(CH_TS.format(ts))
        .eventName("device.crash")
        .pulseType("device.crash")
        .exceptionStackTrace("symbolicated")
        .exceptionStackTraceRaw("raw")
        .groupId("EXC-1")
        .resourceAttributes(Map.of("project.id", "default-project"))
        .logAttributes(Map.of("pulse.type", "device.crash"))
        .build();
  }

  @Nested
  class WhenDisabled {

    @Test
    void shouldReportNotEnabled() {
      StackTraceArchiveConfig config = StackTraceArchiveConfig.builder().enabled(false).build();
      StackTraceArchiveService service = new StackTraceArchiveService(config, s3UploadService);

      assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldNoOpArchiveForEvents() {
      StackTraceArchiveConfig config = StackTraceArchiveConfig.builder().enabled(false).build();
      StackTraceArchiveService service = new StackTraceArchiveService(config, s3UploadService);

      service.archive(List.of(sampleEvent())).test().assertComplete();

      verifyNoInteractions(s3UploadService);
    }

    @Test
    void shouldNoOpArchiveForNullOrEmptyList() {
      StackTraceArchiveConfig config = StackTraceArchiveConfig.builder().enabled(false).build();
      StackTraceArchiveService service = new StackTraceArchiveService(config, s3UploadService);

      service.archive(null).test().assertComplete();
      service.archive(Collections.emptyList()).test().assertComplete();

      verifyNoInteractions(s3UploadService);
    }

    @Test
    void shouldNotThrowOnFlushIfDue() {
      StackTraceArchiveConfig config = StackTraceArchiveConfig.builder().enabled(false).build();
      StackTraceArchiveService service = new StackTraceArchiveService(config, s3UploadService);

      service.flushIfDue();
    }
  }

  @Nested
  class WhenEnabled {

    private StackTraceArchiveConfig enabledConfig() {
      return StackTraceArchiveConfig.builder()
          .enabled(true)
          .s3Bucket("test-bucket")
          .s3Region("us-east-1")
          .stagingDir(tempDir.toString())
          .flushSizeBytes(1L)
          .flushAgeMs(1L)
          .rowGroupBytes(64 * 1024L)
          .pageBytes(1024L)
          .build();
    }

    @Test
    void shouldReportEnabled() {
      StackTraceArchiveService service =
          new StackTraceArchiveService(enabledConfig(), s3UploadService);

      assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void shouldNoOpArchiveForNullOrEmptyList() {
      StackTraceArchiveService service =
          new StackTraceArchiveService(enabledConfig(), s3UploadService);

      service.archive(null).test().assertComplete();
      service.archive(Collections.emptyList()).test().assertComplete();

      verify(s3UploadService, never()).upload(anyString(), anyString(), any(File.class));
    }

    @Test
    void shouldMapEventsAndBufferToSink() {
      whenUploadCompletesImmediately();

      StackTraceArchiveService service =
          new StackTraceArchiveService(enabledConfig(), s3UploadService);

      service.archive(List.of(sampleEvent())).test().assertComplete();

      verify(s3UploadService, timeout(10_000).atLeastOnce())
          .upload(eq("test-bucket"), anyString(), any(File.class));
    }

    @Test
    void shouldCompleteEvenWhenArchiveReturnsCompletable() {
      whenUploadCompletesImmediately();

      StackTraceArchiveService service =
          new StackTraceArchiveService(enabledConfig(), s3UploadService);

      Completable archive = service.archive(List.of(sampleEvent(), sampleEvent()));
      archive.test().assertComplete().assertNoErrors();
    }

    @Test
    void shouldAllowFlushIfDueWithoutError() {
      whenUploadCompletesImmediately();

      StackTraceArchiveService service =
          new StackTraceArchiveService(enabledConfig(), s3UploadService);
      service.archive(List.of(sampleEvent())).test().assertComplete();

      service.flushIfDue();
    }

    private void whenUploadCompletesImmediately() {
      org.mockito.Mockito.when(s3UploadService.upload(anyString(), anyString(), any(File.class)))
          .thenReturn(CompletableFuture.completedFuture(
              PutObjectResponse.builder().eTag("test-etag").build()));
    }
  }
}
