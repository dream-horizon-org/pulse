package org.dreamhorizon.pulses3archiver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class ParquetBatchWriterTest {

  private static final String TEST_BUCKET = "pulse-otel-testproj";

  @TempDir
  Path tempDir;

  private static Schema schema;
  private static Schema metricsSchema;
  private static WriterConfig writerConfig;

  @BeforeAll
  static void setup() throws Exception {
    try (InputStream is = ParquetBatchWriterTest.class.getClassLoader()
        .getResourceAsStream("schemas/otel_traces.avsc")) {
      schema = new Schema.Parser().parse(is);
    }
    try (InputStream is = ParquetBatchWriterTest.class.getClassLoader()
        .getResourceAsStream("schemas/otel_metrics_sum.avsc")) {
      metricsSchema = new Schema.Parser().parse(is);
    }
    writerConfig = new WriterConfig();
    writerConfig.setRowGroupBytes(134217728L);
    writerConfig.setPageBytes(1048576L);
    writerConfig.setFlushSizeBytes(268435456L);
    writerConfig.setFlushAgeMs(300000L);
  }

  private static GenericRecord buildRecord(long timestampMicros) {
    return buildRecordForSchema(schema, timestampMicros);
  }

  private static GenericRecord buildRecordForSchema(Schema avroSchema, long timestampMicros) {
    GenericRecord rec = new GenericData.Record(avroSchema);
    for (Schema.Field f : avroSchema.getFields()) {
      if (f.defaultVal() != null) {
        rec.put(f.name(), GenericData.get().getDefaultValue(f));
      } else {
        Schema fieldSchema = f.schema();
        if (fieldSchema.getType() == Schema.Type.UNION) {
          rec.put(f.name(), null);
        } else if (fieldSchema.getType() == Schema.Type.STRING) {
          rec.put(f.name(), "");
        } else if (fieldSchema.getType() == Schema.Type.LONG) {
          rec.put(f.name(), 0L);
        } else if (fieldSchema.getType() == Schema.Type.INT) {
          rec.put(f.name(), 0);
        } else if (fieldSchema.getType() == Schema.Type.DOUBLE) {
          rec.put(f.name(), 0.0d);
        } else if (fieldSchema.getType() == Schema.Type.BOOLEAN) {
          rec.put(f.name(), false);
        } else if (fieldSchema.getType() == Schema.Type.ARRAY) {
          rec.put(f.name(), Collections.emptyList());
        } else if (fieldSchema.getType() == Schema.Type.MAP) {
          rec.put(f.name(), Collections.emptyMap());
        }
      }
    }
    if (avroSchema.getField("Timestamp") != null) {
      rec.put("Timestamp", timestampMicros);
    }
    if (avroSchema.getField("TimeUnix") != null) {
      rec.put("TimeUnix", timestampMicros);
      if (avroSchema.getField("StartTimeUnix") != null) {
        rec.put("StartTimeUnix", timestampMicros);
      }
    }
    return rec;
  }

  @Test
  void shouldWriteMetricsRowsUsingTimeUnixForPartitioning() throws Exception {
    S3UploadService mockS3 = mock(S3UploadService.class);
    when(mockS3.upload(anyString(), anyString(), any(File.class)))
        .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

    ParquetBatchWriter writer = new ParquetBatchWriter(
        "otel_metrics_sum", metricsSchema, writerConfig, TEST_BUCKET, mockS3,
        tempDir.toString(), "test-node", offsets -> {});

    long baseTimeMicros = 1_700_000_000_000_000L;
    writer.add(List.of(buildRecordForSchema(metricsSchema, baseTimeMicros)), 0, 1L);
    Map<Integer, Long> offsets = writer.flush();
    assertThat(offsets).containsEntry(0, 1L);
  }

  @Test
  void shouldWriteAndReadBackRows() throws Exception {
    S3UploadService mockS3 = mock(S3UploadService.class);
    Map<Integer, Long> flushedOffsets = new HashMap<>();

    when(mockS3.upload(anyString(), anyString(), any(File.class)))
        .thenAnswer(inv -> {
          File f = inv.getArgument(2);
          flushedOffsets.put(-1, (long) f.getAbsolutePath().hashCode());
          return CompletableFuture.completedFuture(PutObjectResponse.builder().build());
        });

    ParquetBatchWriter writer = new ParquetBatchWriter(
        "otel_traces", schema, writerConfig, TEST_BUCKET, mockS3,
        tempDir.toString(), "test-node", offsets -> {});

    long baseTimeMicros = 1_700_000_000_000_000L;
    List<GenericRecord> batch = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      batch.add(buildRecord(baseTimeMicros + i));
    }

    writer.add(batch, 0, 9L);

    Map<Integer, Long> offsets = writer.flush();
    assertThat(offsets).containsEntry(0, 9L);
  }

  @Test
  void shouldTrackPendingOffsets() throws Exception {
    S3UploadService mockS3 = mock(S3UploadService.class);
    when(mockS3.upload(anyString(), anyString(), any(File.class)))
        .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

    ParquetBatchWriter writer = new ParquetBatchWriter(
        "otel_traces", schema, writerConfig, TEST_BUCKET, mockS3,
        tempDir.toString(), "test-node", offsets -> {});

    long ts = 1_700_000_000_000_000L;
    writer.add(List.of(buildRecord(ts)), 0, 5L);
    writer.add(List.of(buildRecord(ts + 1)), 0, 10L);
    writer.add(List.of(buildRecord(ts + 2)), 1, 3L);

    Map<Integer, Long> flushed = writer.flush();

    assertThat(flushed).containsEntry(0, 10L);
    assertThat(flushed).containsEntry(1, 3L);
  }

  @Test
  void shouldReturnCommittedOffsetsAfterSuccessfulUpload() throws Exception {
    Map<Integer, Long> committed = new HashMap<>();
    S3UploadService mockS3 = mock(S3UploadService.class);
    when(mockS3.upload(anyString(), anyString(), any(File.class)))
        .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

    ParquetBatchWriter writer = new ParquetBatchWriter(
        "otel_traces", schema, writerConfig, TEST_BUCKET, mockS3,
        tempDir.toString(), "test-node",
        offsets -> committed.putAll(offsets));

    long ts = 1_700_000_000_000_000L;
    writer.add(List.of(buildRecord(ts)), 0, 7L);
    writer.flush();

    Thread.sleep(100);

    assertThat(committed).containsEntry(0, 7L);
    assertThat(writer.getCommittedOffsets()).containsEntry(0, 7L);
  }

  @Test
  void shouldNotCallOnFlushSuccessOnUploadFailure() throws Exception {
    List<Map<Integer, Long>> successCalls = new ArrayList<>();
    S3UploadService mockS3 = mock(S3UploadService.class);
    CompletableFuture<PutObjectResponse> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("S3 unavailable"));
    when(mockS3.upload(anyString(), anyString(), any(File.class))).thenReturn(failedFuture);

    ParquetBatchWriter writer = new ParquetBatchWriter(
        "otel_traces", schema, writerConfig, TEST_BUCKET, mockS3,
        tempDir.toString(), "test-node",
        successCalls::add);

    long ts = 1_700_000_000_000_000L;
    writer.add(List.of(buildRecord(ts)), 0, 1L);
    writer.flush();
    Thread.sleep(100);

    assertThat(successCalls).isEmpty();
    assertThat(writer.getCommittedOffsets()).isEmpty();
  }
}
