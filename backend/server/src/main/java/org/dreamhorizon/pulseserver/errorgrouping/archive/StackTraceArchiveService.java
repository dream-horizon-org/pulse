package org.dreamhorizon.pulseserver.errorgrouping.archive;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;

@Slf4j
@Singleton
public class StackTraceArchiveService {

  private final StackTraceArchiveConfig config;
  private final Schema schema;
  private final OtelMultiProjectParquetSink sink;

  @Inject
  public StackTraceArchiveService(
      StackTraceArchiveConfig config,
      OtelArchiveS3UploadService s3UploadService) {
    this.config = config;
    if (config.isEnabled()) {
      this.schema = loadSchema();
      this.sink = new OtelMultiProjectParquetSink(
          StackTraceArchiveConfig.TABLE_STACK_TRACE_EVENTS,
          schema,
          OtelParquetWriterConfig.from(config),
          config.getS3Bucket(),
          s3UploadService,
          config.getStagingDir(),
          resolveNodeId());
      log.info("[StackTraceArchive] Enabled bucket={} staging={}",
          config.getS3Bucket(), config.getStagingDir());
    } else {
      this.schema = null;
      this.sink = null;
    }
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  public Completable archive(List<StackTraceEvent> events) {
    if (!config.isEnabled() || events == null || events.isEmpty()) {
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      List<GenericRecord> rows = new ArrayList<>(events.size());
      for (StackTraceEvent event : events) {
        rows.add(StackTraceEventsMapper.toRecord(event, schema));
      }
      sink.add(rows);
    }).doOnError(e -> log.error("[StackTraceArchive] Failed to buffer rows: {}", e.getMessage(), e));
  }

  public void flushIfDue() {
    if (sink != null) {
      sink.flushIfDue();
    }
  }

  private static Schema loadSchema() {
    try (InputStream in = StackTraceArchiveService.class.getClassLoader()
        .getResourceAsStream("schemas/stack_trace_events.avsc")) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource schemas/stack_trace_events.avsc");
      }
      return new Schema.Parser().parse(in);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load stack_trace_events Avro schema", e);
    }
  }

  private static String resolveNodeId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown-node";
    }
  }
}
