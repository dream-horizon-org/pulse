package org.dreamhorizon.pulses3archiver.service;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;

/**
 * Routes rows to one {@link ParquetBatchWriter} per destination S3 bucket (one project bucket each).
 */
@Slf4j
public final class MultiProjectParquetSink {

  private final String tableName;
  private final Schema schema;
  private final WriterConfig writerConfig;
  private final S3UploadService s3UploadService;
  private final String stagingDir;
  private final String nodeId;
  private final Runnable onFlushSuccess;

  private final ConcurrentHashMap<String, ParquetBatchWriter> writers = new ConcurrentHashMap<>();

  public MultiProjectParquetSink(
      String tableName,
      Schema schema,
      WriterConfig writerConfig,
      S3UploadService s3UploadService,
      String stagingDir,
      String nodeId,
      Runnable onFlushSuccess) {
    this.tableName = tableName;
    this.schema = schema;
    this.writerConfig = writerConfig;
    this.s3UploadService = s3UploadService;
    this.stagingDir = stagingDir;
    this.nodeId = nodeId;
    this.onFlushSuccess = onFlushSuccess == null ? () -> {} : onFlushSuccess;
  }

  public void add(List<GenericRecord> rows, int partition, long offset) throws IOException {
    if (rows.isEmpty()) {
      return;
    }
    Map<String, List<GenericRecord>> byProject = RecordGroupingByProject.partitionByProjectId(rows);
    for (Map.Entry<String, List<GenericRecord>> e : byProject.entrySet()) {
      String bucket = ProjectBucketNames.toBucket(e.getKey());
      writerFor(bucket).add(e.getValue(), partition, offset);
    }
  }

  private ParquetBatchWriter writerFor(String bucket) {
    return writers.computeIfAbsent(bucket, b -> new ParquetBatchWriter(
        tableName,
        schema,
        writerConfig,
        bucket,
        s3UploadService,
        stagingDir,
        nodeId,
        offsetsIgnored -> {
          try {
            onFlushSuccess.run();
          } catch (RuntimeException ex) {
            log.error("[{}] post-flush callback failed: {}", tableName, ex.getMessage(), ex);
          }
        }));
  }

  public void flushIfDue() {
    writers.values().forEach(ParquetBatchWriter::flushIfDue);
  }

  public void flush() throws IOException {
    for (ParquetBatchWriter w : writers.values()) {
      w.flush();
    }
  }

  public Collection<ParquetBatchWriter> allWriters() {
    return List.copyOf(writers.values());
  }

  public void writeDlq(byte[] rawBytes, String code, String message, String topic, int partition, long offset) {
    log.error("[{}][DLQ] topic={} partition={} offset={} error={}: {}",
        tableName, topic, partition, offset, code, message);
  }
}
