package org.dreamhorizon.pulses3archiver.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;

@Slf4j
public class ParquetBatchWriter {

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter PART_FMT =
      DateTimeFormatter.ofPattern("'year='yyyy/'month='MM/'day='dd").withZone(ZoneOffset.UTC);

  private final String tableName;
  private final Schema schema;
  private final WriterConfig writerConfig;
  private final String s3Bucket;
  private final S3UploadService s3UploadService;
  private final String stagingSegment;
  private final String stagingRoot;
  private final String nodeId;
  private final Consumer<Map<Integer, Long>> onFlushSuccess;

  private ParquetWriter<GenericRecord> currentWriter;
  private Path currentTmpFile;
  private long rowsInCurrentFile;
  private long firstRowNanos;
  private long fileOpenTimeMs;

  private final Map<Integer, Long> pendingOffsets = new HashMap<>();
  private volatile Map<Integer, Long> committedOffsets = new HashMap<>();
  private final Set<Integer> partitionsTracked = ConcurrentHashMap.newKeySet();

  /**
   * @param s3Bucket destination bucket for this sink (typically {@code pulse-otel-*})
   * @param stagingRoot base staging directory (typically archiver stagingDir)
   * @param stagingSegment filesystem subdirectory for this bucket (safe segment derived from bucket)
   */
  public ParquetBatchWriter(
      String tableName,
      Schema schema,
      WriterConfig writerConfig,
      String s3Bucket,
      S3UploadService s3UploadService,
      String stagingRoot,
      String stagingSegment,
      String nodeId,
      Consumer<Map<Integer, Long>> onFlushSuccess) {
    this.tableName = Objects.requireNonNull(tableName);
    this.schema = Objects.requireNonNull(schema);
    this.writerConfig = Objects.requireNonNull(writerConfig);
    this.s3Bucket = Objects.requireNonNull(s3Bucket, "s3Bucket");
    this.s3UploadService = Objects.requireNonNull(s3UploadService);
    this.stagingRoot = Objects.requireNonNull(stagingRoot);
    this.stagingSegment = Objects.requireNonNull(stagingSegment);
    this.nodeId = Objects.requireNonNull(nodeId);
    this.onFlushSuccess = onFlushSuccess;
    Path base = stagingPathBase();
    try {
      Files.createDirectories(base);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create staging dir " + base, e);
    }
  }

  /** Back-compat ctor: derives {@code stagingSegment} from bucket name for local paths. */
  public ParquetBatchWriter(
      String tableName,
      Schema schema,
      WriterConfig writerConfig,
      String s3Bucket,
      S3UploadService s3UploadService,
      String stagingRoot,
      String nodeId,
      Consumer<Map<Integer, Long>> onFlushSuccess) {
    this(
        tableName,
        schema,
        writerConfig,
        s3Bucket,
        s3UploadService,
        stagingRoot,
        stagingFilesystemSegment(s3Bucket),
        nodeId,
        onFlushSuccess);
  }

  static String stagingFilesystemSegment(String bucketName) {
    String b = bucketName == null ? "unknown-bucket" : bucketName.trim();
    if (b.isEmpty()) {
      return "unknown-bucket";
    }
    StringBuilder sb = new StringBuilder(b.length());
    for (int i = 0; i < b.length(); i++) {
      char c = b.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
          || c == '-' || c == '_') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    return sb.toString();
  }

  private Path stagingPathBase() {
    return Path.of(stagingRoot, tableName, stagingSegment);
  }

  public synchronized void add(List<GenericRecord> records, int partition, long offset)
      throws IOException {
    if (records.isEmpty()) {
      return;
    }
    partitionsTracked.add(partition);
    if (currentWriter == null) {
      openNewFile(records.get(0));
    }
    for (GenericRecord record : records) {
      currentWriter.write(record);
      rowsInCurrentFile++;
    }
    pendingOffsets.merge(partition, offset, Math::max);

    long fileSizeBytes = currentTmpFile.toFile().length();
    long ageMs = System.currentTimeMillis() - fileOpenTimeMs;
    int currentHour = Instant.ofEpochSecond(0, firstRowNanos).atOffset(ZoneOffset.UTC).getHour();
    int nowHour = Instant.now().atOffset(ZoneOffset.UTC).getHour();

    if (fileSizeBytes >= writerConfig.getFlushSizeBytes()
        || ageMs >= writerConfig.getFlushAgeMs()
        || currentHour != nowHour) {
      flush();
    }
  }

  public synchronized void flushIfDue() {
    if (currentWriter == null || rowsInCurrentFile == 0) {
      return;
    }
    long ageMs = System.currentTimeMillis() - fileOpenTimeMs;
    if (ageMs >= writerConfig.getFlushAgeMs()) {
      try {
        flush();
      } catch (IOException e) {
        log.error("[{}] flushIfDue failed: {}", tableName, e.getMessage(), e);
      }
    }
  }

  public synchronized Map<Integer, Long> flush() throws IOException {
    if (currentWriter == null) {
      return committedOffsets;
    }
    currentWriter.close();
    currentWriter = null;

    String s3Key = buildS3Key();
    File tmpFile = currentTmpFile.toFile();
    Map<Integer, Long> offsets = new HashMap<>(pendingOffsets);
    pendingOffsets.clear();
    long rowsFlushed = rowsInCurrentFile;
    rowsInCurrentFile = 0;
    currentTmpFile = null;

    s3UploadService.upload(s3Bucket, s3Key, tmpFile)
        .whenComplete((resp, err) -> {
          if (err != null) {
            log.error("[{}] S3 upload failed for s3://{}/{} : {}", tableName, s3Bucket, s3Key,
                err.getMessage());
          } else {
            synchronized (ParquetBatchWriter.this) {
              committedOffsets = offsets;
            }
            try {
              Files.deleteIfExists(tmpFile.toPath());
            } catch (IOException ex) {
              log.warn("[{}] Could not delete tmp file {}: {}", tableName, tmpFile, ex.getMessage());
            }
            log.info("[{}] Flushed {} rows to s3://{}/{}", tableName, rowsFlushed, s3Bucket, s3Key);
            if (onFlushSuccess != null) {
              onFlushSuccess.accept(offsets);
            }
          }
        });

    return offsets;
  }

  private void openNewFile(GenericRecord firstRecord) throws IOException {
    firstRowNanos = getTimestampNanos(firstRecord);
    fileOpenTimeMs = System.currentTimeMillis();
    rowsInCurrentFile = 0;

    Path dir = stagingPathBase();
    Files.createDirectories(dir);
    currentTmpFile = Files.createTempFile(dir, tableName + "-", ".parquet");
    Files.delete(currentTmpFile);

    org.apache.hadoop.fs.Path hadoopPath =
        new org.apache.hadoop.fs.Path(currentTmpFile.toUri());

    currentWriter = AvroParquetWriter.<GenericRecord>builder(hadoopPath)
        .withSchema(schema)
        .withCompressionCodec(CompressionCodecName.ZSTD)
        .withRowGroupSize(writerConfig.getRowGroupBytes())
        .withPageSize((int) writerConfig.getPageBytes())
        .withDictionaryEncoding(true)
        .withConf(new Configuration())
        .build();

    log.debug("[{}] Opened new parquet file: {}", tableName, currentTmpFile);
  }

  private String buildS3Key() {
    Instant ts = Instant.ofEpochSecond(0, firstRowNanos);
    String partition = PART_FMT.format(ts);
    String isoTs = TS_FMT.format(ts);
    String uuid = UUID.randomUUID().toString().replace("-", "");
    return String.format("%s/%s/%s_%s_%s.parquet", tableName, partition, isoTs, nodeId, uuid);
  }

  // Traces/logs: Timestamp. Metrics: TimeUnix. Values are micros; return nanos for partitioning.
  private long getTimestampNanos(GenericRecord record) {
    Schema s = record.getSchema();
    Long micros = extractTimestampMicros(s, record, "Timestamp");
    if (micros == null) {
      micros = extractTimestampMicros(s, record, "TimeUnix");
    }
    if (micros != null) {
      return micros * 1000L;
    }
    return System.currentTimeMillis() * 1_000_000L;
  }

  private static Long extractTimestampMicros(Schema s, GenericRecord record, String field) {
    if (s.getField(field) == null) {
      return null;
    }
    Object val = record.get(field);
    return val instanceof Long l ? l : null;
  }

  public synchronized Map<Integer, Long> getCommittedOffsets() {
    return new HashMap<>(committedOffsets);
  }

  public Set<Integer> getPartitionsTracked() {
    return Set.copyOf(partitionsTracked);
  }

  public boolean tracksPartition(int partition) {
    return partitionsTracked.contains(partition);
  }

  public void writeDlq(byte[] rawBytes, String errorCode, String errorMsg,
      String topic, int partition, long offset) {
    log.error("[{}][DLQ] topic={} partition={} offset={} error={}: {}",
        tableName, topic, partition, offset, errorCode, errorMsg);
  }
}
