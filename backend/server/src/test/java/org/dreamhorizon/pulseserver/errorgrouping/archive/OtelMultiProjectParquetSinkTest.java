package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class OtelMultiProjectParquetSinkTest {

  private static Schema schema;

  @Mock
  private OtelArchiveS3UploadService s3UploadService;

  @TempDir
  Path tempDir;

  private OtelMultiProjectParquetSink sink;

  @BeforeAll
  static void loadSchema() throws Exception {
    ParquetTestSupport.ensureHadoopHome();
    try (InputStream in = OtelMultiProjectParquetSinkTest.class.getClassLoader()
        .getResourceAsStream("schemas/stack_trace_events.avsc")) {
      schema = new Schema.Parser().parse(in);
    }
  }

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(s3UploadService.upload(anyString(), anyString(), any(File.class)))
        .thenReturn(CompletableFuture.completedFuture(
            PutObjectResponse.builder().eTag("etag").build()));

    OtelParquetWriterConfig writerConfig = new OtelParquetWriterConfig(
        64 * 1024L, 1024L, 1L, 1L);

    sink = new OtelMultiProjectParquetSink(
        StackTraceArchiveConfig.TABLE_STACK_TRACE_EVENTS,
        schema,
        writerConfig,
        "test-bucket",
        s3UploadService,
        tempDir.toString(),
        "test-node");
  }

  private GenericRecord row(String projectId, long timestampMicros) {
    GenericRecord rec = new GenericData.Record(schema);
    rec.put("ProjectId", projectId);
    rec.put("Timestamp", timestampMicros);
    rec.put("EventName", "device.crash");
    rec.put("Title", "");
    rec.put("ExceptionStackTrace", "");
    rec.put("ExceptionStackTraceRaw", "");
    rec.put("ExceptionMessage", "");
    rec.put("ExceptionType", "");
    rec.put("Interactions", List.of());
    rec.put("ScreenName", "");
    rec.put("UserId", "");
    rec.put("SessionId", "");
    rec.put("Platform", "");
    rec.put("OsVersion", "");
    rec.put("DeviceModel", "");
    rec.put("AppVersionCode", "");
    rec.put("AppVersion", "");
    rec.put("SdkVersion", "");
    rec.put("BundleId", "");
    rec.put("TraceId", "");
    rec.put("SpanId", "");
    rec.put("GroupId", "");
    rec.put("Signature", "");
    rec.put("Fingerprint", "");
    rec.put("ScopeAttributes", java.util.Map.of());
    rec.put("LogAttributes", java.util.Map.of());
    rec.put("ResourceAttributes", java.util.Map.of());
    rec.put("PulseType", "device.crash");
    rec.put("MeteringSessionId", "");
    rec.put("AppInstallationId", "");
    return rec;
  }

  @Nested
  class Add {

    @Test
    void shouldNoOpForEmptyRows() throws Exception {
      sink.add(List.of());

      verify(s3UploadService, org.mockito.Mockito.never())
          .upload(anyString(), anyString(), any(File.class));
    }

    @Test
    void shouldRouteRowsToPerProjectWriters() throws Exception {
      GenericRecord projA = row("proj_alpha", 1_746_000_000_000_000L);
      GenericRecord projB = row("proj_beta", 1_746_000_000_000_000L);

      sink.add(List.of(projA, projB));

      Path stagingA = tempDir.resolve("stack_trace_events").resolve("proj-alpha");
      Path stagingB = tempDir.resolve("stack_trace_events").resolve("proj-beta");
      assertThat(Files.exists(stagingA) || Files.exists(stagingB)).isTrue();

      verify(s3UploadService, timeout(10_000).atLeast(2))
          .upload(eq("test-bucket"), anyString(), any(File.class));
    }

    @Test
    void shouldUseUnknownPrefixForBlankProjectId() throws Exception {
      GenericRecord unknown = row("  ", 1_746_000_000_000_000L);

      sink.add(List.of(unknown));

      Path unknownStaging = tempDir.resolve("stack_trace_events")
          .resolve(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
      assertThat(Files.exists(unknownStaging)).isTrue();
    }
  }

  @Nested
  class FlushIfDue {

    @Test
    void shouldNotThrowWhenNoBufferedRows() {
      sink.flushIfDue();
    }

    @Test
    void shouldFlushBufferedRowsWhenAgeExceeded() throws Exception {
      GenericRecord rec = row("flush-proj", 1_746_000_000_000_000L);
      sink.add(List.of(rec));

      Thread.sleep(5);
      sink.flushIfDue();

      verify(s3UploadService, timeout(10_000).atLeastOnce())
          .upload(eq("test-bucket"), anyString(), any(File.class));
    }
  }
}
