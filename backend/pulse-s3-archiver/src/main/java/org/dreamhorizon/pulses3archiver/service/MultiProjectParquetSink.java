package org.dreamhorizon.pulses3archiver.service;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;

/**
 * Routes rows to one {@link ParquetBatchWriter} per project. All writers share the same
 * ingestion bucket ({@code rootBucket}); they differ only by the {@code <project-id>/} key prefix.
 *
 * <p>Resulting object keys: {@code s3://<rootBucket>/<projectId>/<table>/year=.../day=.../*.parquet}.
 */
@Slf4j
public final class MultiProjectParquetSink {

  private final String tableName;
  private final Schema schema;
  private final WriterConfig writerConfig;
  private final String rootBucket;
  private final S3UploadService s3UploadService;
  private final String stagingDir;
  private final String nodeId;
  private final Runnable onFlushSuccess;

  /** keyed by sanitized project prefix (e.g. {@code "default-project"}, {@code "unknown"}) */
  private final ConcurrentHashMap<String, ParquetBatchWriter> writers = new ConcurrentHashMap<>();

  public MultiProjectParquetSink(
      String tableName,
      Schema schema,
      WriterConfig writerConfig,
      String rootBucket,
      S3UploadService s3UploadService,
      String stagingDir,
      String nodeId,
      Runnable onFlushSuccess) {
    this.tableName = tableName;
    this.schema = schema;
    this.writerConfig = writerConfig;
    this.rootBucket = rootBucket;
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
      String prefix = ProjectKeyPrefixes.toPrefix(e.getKey());
      writerFor(prefix).add(e.getValue(), partition, offset);
    }
  }

  private ParquetBatchWriter writerFor(String prefix) {
    return writers.computeIfAbsent(prefix, p -> new ParquetBatchWriter(
        tableName,
        schema,
        writerConfig,
        rootBucket,
        p,
        s3UploadService,
        stagingDir,
        p,
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

  public void writeDlq(byte[] rawBytes, String code, String message,
      String topic, int partition, long offset) {
    log.error("[{}][DLQ] topic={} partition={} offset={} error={}: {}",
        tableName, topic, partition, offset, code, message);
  }
}
