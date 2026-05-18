package org.dreamhorizon.pulseserver.errorgrouping.archive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

@Slf4j
public class OtelParquetBatchWriter {

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter PART_FMT =
      DateTimeFormatter.ofPattern("'year='yyyy/'month='MM/'day='dd").withZone(ZoneOffset.UTC);

  private final String tableName;
  private final Schema schema;
  private final OtelParquetWriterConfig writerConfig;
  private final String s3Bucket;
  private final String keyPrefix;
  private final OtelArchiveS3UploadService s3UploadService;
  private final String stagingSegment;
  private final String stagingRoot;
  private final String nodeId;

  private ParquetWriter<GenericRecord> currentWriter;
  private Path currentTmpFile;
  private long rowsInCurrentFile;
  private long firstRowNanos;
  private long fileOpenTimeMs;

  public OtelParquetBatchWriter(
      String tableName,
      Schema schema,
      OtelParquetWriterConfig writerConfig,
      String s3Bucket,
      String keyPrefix,
      OtelArchiveS3UploadService s3UploadService,
      String stagingRoot,
      String stagingSegment,
      String nodeId) {
    this.tableName = Objects.requireNonNull(tableName);
    this.schema = Objects.requireNonNull(schema);
    this.writerConfig = Objects.requireNonNull(writerConfig);
    this.s3Bucket = Objects.requireNonNull(s3Bucket, "s3Bucket");
    this.keyPrefix = normalizePrefix(keyPrefix);
    this.s3UploadService = Objects.requireNonNull(s3UploadService);
    this.stagingRoot = Objects.requireNonNull(stagingRoot);
    this.stagingSegment = Objects.requireNonNull(stagingSegment);
    this.nodeId = Objects.requireNonNull(nodeId);
    Path base = stagingPathBase();
    try {
      Files.createDirectories(base);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create staging dir " + base, e);
    }
  }

  private static String normalizePrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return "";
    }
    String p = prefix.trim();
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    while (p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    return p;
  }

  private Path stagingPathBase() {
    return Path.of(stagingRoot, tableName, stagingSegment);
  }

  public synchronized void add(List<GenericRecord> records) throws IOException {
    if (records.isEmpty()) {
      return;
    }
    if (currentWriter == null) {
      openNewFile(records.get(0));
    }
    for (GenericRecord record : records) {
      currentWriter.write(record);
      rowsInCurrentFile++;
    }

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

  public synchronized void flush() throws IOException {
    if (currentWriter == null) {
      return;
    }
    currentWriter.close();
    currentWriter = null;

    String s3Key = buildS3Key();
    File tmpFile = currentTmpFile.toFile();
    long rowsFlushed = rowsInCurrentFile;
    rowsInCurrentFile = 0;
    currentTmpFile = null;

    s3UploadService.upload(s3Bucket, s3Key, tmpFile)
        .whenComplete((resp, err) -> {
          if (err == null) {
            try {
              Files.deleteIfExists(tmpFile.toPath());
            } catch (IOException ex) {
              log.warn("[{}] Could not delete tmp file {}: {}", tableName, tmpFile, ex.getMessage());
            }
            log.info("[{}] Flushed {} rows to s3://{}/{}", tableName, rowsFlushed, s3Bucket, s3Key);
          }
        });
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
  }

  private String buildS3Key() {
    Instant ts = Instant.ofEpochSecond(0, firstRowNanos);
    String partition = PART_FMT.format(ts);
    String isoTs = TS_FMT.format(ts);
    String uuid = UUID.randomUUID().toString().replace("-", "");
    String tail = String.format("%s/%s/%s_%s_%s.parquet", tableName, partition, isoTs, nodeId, uuid);
    return keyPrefix.isEmpty() ? tail : keyPrefix + "/" + tail;
  }

  private long getTimestampNanos(GenericRecord record) {
    Object val = record.get("Timestamp");
    if (val instanceof Long micros) {
      return micros * 1000L;
    }
    return System.currentTimeMillis() * 1_000_000L;
  }
}
