package org.dreamhorizon.pulseserver.errorgrouping.archive;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

@Slf4j
public final class OtelMultiProjectParquetSink {

  private final String tableName;
  private final Schema schema;
  private final OtelParquetWriterConfig writerConfig;
  private final String rootBucket;
  private final OtelArchiveS3UploadService s3UploadService;
  private final String stagingDir;
  private final String nodeId;

  private final ConcurrentHashMap<String, OtelParquetBatchWriter> writers = new ConcurrentHashMap<>();

  public OtelMultiProjectParquetSink(
      String tableName,
      Schema schema,
      OtelParquetWriterConfig writerConfig,
      String rootBucket,
      OtelArchiveS3UploadService s3UploadService,
      String stagingDir,
      String nodeId) {
    this.tableName = tableName;
    this.schema = schema;
    this.writerConfig = writerConfig;
    this.rootBucket = rootBucket;
    this.s3UploadService = s3UploadService;
    this.stagingDir = stagingDir;
    this.nodeId = nodeId;
  }

  public void add(List<GenericRecord> rows) throws IOException {
    if (rows.isEmpty()) {
      return;
    }
    Map<String, List<GenericRecord>> byProject = RecordGroupingByProject.partitionByProjectId(rows);
    for (Map.Entry<String, List<GenericRecord>> e : byProject.entrySet()) {
      String prefix = ProjectKeyPrefixes.toPrefix(e.getKey());
      writerFor(prefix).add(e.getValue());
    }
  }

  public void flushIfDue() {
    writers.values().forEach(OtelParquetBatchWriter::flushIfDue);
  }

  private OtelParquetBatchWriter writerFor(String prefix) {
    return writers.computeIfAbsent(prefix, p -> new OtelParquetBatchWriter(
        tableName,
        schema,
        writerConfig,
        rootBucket,
        p,
        s3UploadService,
        stagingDir,
        p,
        nodeId));
  }
}
