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
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.dreamhorizon.pulses3archiver.config.S3Config;
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
  private final S3Config s3Config;
  private final S3UploadService s3UploadService;
  private final String stagingDir;
  private final String nodeId;
  private final Consumer<Map<Integer, Long>> onFlushSuccess;

  private ParquetWriter<GenericRecord> currentWriter;
  private Path currentTmpFile;
  private long rowsInCurrentFile;
  private long firstRowNanos;
  private long fileOpenTimeMs;

  private final Map<Integer, Long> pendingOffsets = new HashMap<>();
  private volatile Map<Integer, Long> committedOffsets = new HashMap<>();

  public ParquetBatchWriter(
      String tableName,
      Schema schema,
      WriterConfig writerConfig,
      S3Config s3Config,
      S3UploadService s3UploadService,
      String stagingDir,
      String nodeId,
      Consumer<Map<Integer, Long>> onFlushSuccess) {
    this.tableName = tableName;
    this.schema = schema;
    this.writerConfig = writerConfig;
    this.s3Config = s3Config;
    this.s3UploadService = s3UploadService;
    this.stagingDir = stagingDir;
    this.nodeId = nodeId;
    this.onFlushSuccess = onFlushSuccess;
    try {
      Files.createDirectories(Path.of(stagingDir, tableName));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create staging dir for " + tableName, e);
    }
  }

  public synchronized void add(List<GenericRecord> records, int partition, long offset)
      throws IOException {
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
    // Snapshot and clear pending offsets synchronously; new adds start a fresh batch.
    Map<Integer, Long> offsets = new HashMap<>(pendingOffsets);
    pendingOffsets.clear();
    long rowsFlushed = rowsInCurrentFile;
    rowsInCurrentFile = 0;
    currentTmpFile = null;

    s3UploadService.upload(s3Key, tmpFile)
        .whenComplete((resp, err) -> {
          if (err != null) {
            log.error("[{}] S3 upload failed for {}: {}", tableName, s3Key, err.getMessage());
          } else {
            synchronized (this) {
              committedOffsets = offsets;
            }
            try {
              Files.deleteIfExists(tmpFile.toPath());
            } catch (IOException ex) {
              log.warn("[{}] Could not delete tmp file {}: {}", tableName, tmpFile, ex.getMessage());
            }
            log.info("[{}] Flushed {} rows to s3://{}/{}", tableName, rowsFlushed,
                s3Config.getBucket(), s3Key);
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

    Path dir = Path.of(stagingDir, tableName);
    Files.createDirectories(dir);
    // createTempFile reserves the name; delete placeholder so Parquet creates the real file.
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

  private long getTimestampNanos(GenericRecord record) {
    Object ts = record.get("Timestamp");
    if (ts instanceof Long l) {
      return l * 1000L; // stored as micros, convert to nanos for hour-boundary calc
    }
    return System.currentTimeMillis() * 1_000_000L;
  }

  public synchronized Map<Integer, Long> getCommittedOffsets() {
    return new HashMap<>(committedOffsets);
  }

  public void writeDlq(byte[] rawBytes, String errorCode, String errorMsg,
      String topic, int partition, long offset) {
    log.error("[{}][DLQ] topic={} partition={} offset={} error={}: {}",
        tableName, topic, partition, offset, errorCode, errorMsg);
  }
}
