package org.dreamhorizon.pulses3archiver.verticle;

import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.dreamhorizon.pulses3archiver.config.ArchiverConfig;
import org.dreamhorizon.pulses3archiver.config.KafkaConfig;
import org.dreamhorizon.pulses3archiver.config.S3Config;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;
import org.dreamhorizon.pulses3archiver.constant.Constants;
import org.dreamhorizon.pulses3archiver.guice.GuiceInjector;
import org.dreamhorizon.pulses3archiver.mapper.MetricsOtlpMapper;
import org.dreamhorizon.pulses3archiver.mapper.MetricsOtlpMapper.MetricTable;
import org.dreamhorizon.pulses3archiver.service.KafkaCommittedOffsetAggregator;
import org.dreamhorizon.pulses3archiver.service.MultiProjectParquetSink;
import org.dreamhorizon.pulses3archiver.service.ParquetBatchWriter;
import org.dreamhorizon.pulses3archiver.service.S3UploadService;
import org.dreamhorizon.pulses3archiver.util.SharedDataUtils;

@Slf4j
public class MetricsConsumerVerticle extends AbstractVerticle {

  private KafkaConsumer<String, byte[]> consumer;
  private final EnumMap<MetricTable, MultiProjectParquetSink> sinks =
      new EnumMap<>(MetricTable.class);
  private String metricsTopic = "";
  private long flushTimerId = -1;

  @Override
  public void start(Promise<Void> startPromise) {
    KafkaConfig kafkaConfig = SharedDataUtils.get(vertx, KafkaConfig.class);
    WriterConfig writerConfig = SharedDataUtils.get(vertx, WriterConfig.class);
    ArchiverConfig archiverConfig = SharedDataUtils.get(vertx, ArchiverConfig.class);
    S3Config s3Config = SharedDataUtils.get(vertx, S3Config.class);
    String rootBucket = s3Config.getRootBucket();
    S3UploadService s3UploadService = GuiceInjector.getGuiceInjector().getInstance(S3UploadService.class);

    Map<MetricTable, Schema> schemas = loadSchemas();
    String nodeId = resolveNodeId();
    String stagingDir = archiverConfig.getStagingDir();
    metricsTopic = kafkaConfig.getTopicMetrics();
    Runnable onFlushSuccess = () -> maybeCommitKafkaOffsets(metricsTopic);

    sinks.put(MetricTable.SUM, new MultiProjectParquetSink(
        Constants.TABLE_METRICS_SUM, schemas.get(MetricTable.SUM),
        writerConfig, rootBucket, s3UploadService, stagingDir, nodeId, onFlushSuccess));

    sinks.put(MetricTable.HISTOGRAM, new MultiProjectParquetSink(
        Constants.TABLE_METRICS_HISTOGRAM, schemas.get(MetricTable.HISTOGRAM),
        writerConfig, rootBucket, s3UploadService, stagingDir, nodeId, onFlushSuccess));

    sinks.put(MetricTable.EXP_HISTOGRAM, new MultiProjectParquetSink(
        Constants.TABLE_METRICS_EXP_HISTOGRAM, schemas.get(MetricTable.EXP_HISTOGRAM),
        writerConfig, rootBucket, s3UploadService, stagingDir, nodeId, onFlushSuccess));

    sinks.put(MetricTable.SUMMARY, new MultiProjectParquetSink(
        Constants.TABLE_METRICS_SUMMARY, schemas.get(MetricTable.SUMMARY),
        writerConfig, rootBucket, s3UploadService, stagingDir, nodeId, onFlushSuccess));

    consumer = KafkaConsumer.create(vertx, buildKafkaConfig(kafkaConfig));

    consumer.handler(record -> {
      byte[] value = record.value();
      if (value == null || value.length == 0) {
        return;
      }
      try {
        ExportMetricsServiceRequest req = ExportMetricsServiceRequest.parseFrom(value);
        Map<MetricTable, List<GenericRecord>> fanout = MetricsOtlpMapper.map(req, schemas);
        for (MetricTable table : MetricTable.values()) {
          List<GenericRecord> rows = fanout.get(table);
          if (!rows.isEmpty()) {
            sinks.get(table).add(rows, record.partition(), record.offset());
          }
        }
      } catch (InvalidProtocolBufferException e) {
        log.error("[ARCHIVER-METRICS] OTLP parse error partition={} offset={}: {}",
            record.partition(), record.offset(), e.getMessage());
      } catch (IOException e) {
        log.error("[ARCHIVER-METRICS] Parquet write error partition={} offset={}: {}",
            record.partition(), record.offset(), e.getMessage());
      }
    });

    consumer.subscribe(metricsTopic)
        .onSuccess(v -> {
          log.info("[ARCHIVER-METRICS] Subscribed to {} bootstrapServers={}",
              metricsTopic, kafkaConfig.getBootstrapServers());
          flushTimerId = vertx.setPeriodic(archiverConfig.getFlushIntervalMs(),
              id -> sinks.values().forEach(MultiProjectParquetSink::flushIfDue));
          startPromise.complete();
        })
        .onFailure(err -> {
          log.error("[ARCHIVER-METRICS] Failed to subscribe: {}", err.getMessage());
          startPromise.fail(err);
        });
  }

  /**
   * Commits Kafka offsets using conservative merge across every Parquet sink (metric × project bucket).
   */
  private void maybeCommitKafkaOffsets(String topic) {
    List<ParquetBatchWriter> all = new ArrayList<>();
    for (MultiProjectParquetSink sink : sinks.values()) {
      all.addAll(sink.allWriters());
    }
    Map<Integer, Long> minOffsets = KafkaCommittedOffsetAggregator.mergeAcrossWriters(all);
    if (minOffsets.isEmpty()) {
      return;
    }
    Map<TopicPartition, OffsetAndMetadata> toCommit = new HashMap<>();
    for (Map.Entry<Integer, Long> e : minOffsets.entrySet()) {
      toCommit.put(new TopicPartition(topic, e.getKey()),
          new OffsetAndMetadata().setOffset(e.getValue() + 1));
    }
    consumer.commit(toCommit)
        .onSuccess(v -> log.debug("[ARCHIVER-METRICS] Committed Kafka offsets: {}", minOffsets))
        .onFailure(err -> log.error("[ARCHIVER-METRICS] Kafka commit failed: {}", err.getMessage()));
  }

  @Override
  public void stop() {
    if (flushTimerId >= 0) {
      vertx.cancelTimer(flushTimerId);
    }
    for (MultiProjectParquetSink s : sinks.values()) {
      try {
        s.flush();
      } catch (IOException e) {
        log.warn("[ARCHIVER-METRICS] Final flush on stop failed: {}", e.getMessage());
      }
    }
    if (consumer != null) {
      consumer.close();
    }
  }

  private static Map<MetricTable, Schema> loadSchemas() {
    Map<MetricTable, Schema> schemas = new EnumMap<>(MetricTable.class);
    schemas.put(MetricTable.SUM, loadSchema("schemas/otel_metrics_sum.avsc"));
    schemas.put(MetricTable.HISTOGRAM, loadSchema("schemas/otel_metrics_histogram.avsc"));
    schemas.put(MetricTable.EXP_HISTOGRAM, loadSchema("schemas/otel_metrics_exp_histogram.avsc"));
    schemas.put(MetricTable.SUMMARY, loadSchema("schemas/otel_metrics_summary.avsc"));
    return schemas;
  }

  private static Schema loadSchema(String resourcePath) {
    try (InputStream is = MetricsConsumerVerticle.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IllegalStateException("Schema not found: " + resourcePath);
      }
      return new Schema.Parser().parse(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load schema: " + resourcePath, e);
    }
  }

  private static Map<String, String> buildKafkaConfig(KafkaConfig cfg) {
    Map<String, String> map = new HashMap<>();
    map.put("bootstrap.servers", cfg.getBootstrapServers());
    map.put("group.id", cfg.getGroupId());
    map.put("client.id", cfg.getClientId() + "-metrics");
    map.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    map.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    map.put("auto.offset.reset", cfg.getAutoOffsetReset());
    map.put("enable.auto.commit", "false");
    map.put("max.poll.records", String.valueOf(cfg.getMaxPollRecords()));
    map.put("fetch.min.bytes", String.valueOf(cfg.getFetchMinBytes()));
    map.put("fetch.max.wait.ms", String.valueOf(cfg.getFetchMaxWaitMs()));
    return map;
  }

  private static String resolveNodeId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
