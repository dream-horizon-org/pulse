package org.dreamhorizon.pulses3archiver.verticle;

import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
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
import org.dreamhorizon.pulses3archiver.mapper.TracesOtlpMapper;
import org.dreamhorizon.pulses3archiver.service.KafkaCommittedOffsetAggregator;
import org.dreamhorizon.pulses3archiver.service.MultiProjectParquetSink;
import org.dreamhorizon.pulses3archiver.service.S3UploadService;
import org.dreamhorizon.pulses3archiver.util.SharedDataUtils;

@Slf4j
public class TracesConsumerVerticle extends AbstractVerticle {

  private KafkaConsumer<String, byte[]> consumer;
  private MultiProjectParquetSink sink;
  private long flushTimerId = -1;

  @Override
  public void start(Promise<Void> startPromise) {
    KafkaConfig kafkaConfig = SharedDataUtils.get(vertx, KafkaConfig.class);
    WriterConfig writerConfig = SharedDataUtils.get(vertx, WriterConfig.class);
    ArchiverConfig archiverConfig = SharedDataUtils.get(vertx, ArchiverConfig.class);
    S3Config s3Config = SharedDataUtils.get(vertx, S3Config.class);
    S3UploadService s3UploadService = GuiceInjector.getGuiceInjector().getInstance(S3UploadService.class);

    Schema schema = loadSchema("schemas/otel_traces.avsc");
    String nodeId = resolveNodeId();

    consumer = KafkaConsumer.create(vertx, buildKafkaConfig(kafkaConfig));

    sink = new MultiProjectParquetSink(
        Constants.TABLE_TRACES, schema, writerConfig, s3Config.getRootBucket(), s3UploadService,
        archiverConfig.getStagingDir(), nodeId,
        () -> maybeCommitKafkaOffsets(kafkaConfig.getTopicTraces()));

    consumer.handler(record -> {
      byte[] value = record.value();
      if (value == null || value.length == 0) {
        return;
      }
      try {
        ExportTraceServiceRequest req = ExportTraceServiceRequest.parseFrom(value);
        List<GenericRecord> rows = TracesOtlpMapper.map(req, schema);
        sink.add(rows, record.partition(), record.offset());
      } catch (InvalidProtocolBufferException e) {
        log.error("[ARCHIVER-TRACES] OTLP parse error partition={} offset={}: {}",
            record.partition(), record.offset(), e.getMessage());
        sink.writeDlq(value, "S3A1002", e.getMessage(),
            kafkaConfig.getTopicTraces(), record.partition(), record.offset());
      } catch (IOException e) {
        log.error("[ARCHIVER-TRACES] Parquet write error partition={} offset={}: {}",
            record.partition(), record.offset(), e.getMessage());
      }
    });

    consumer.subscribe(kafkaConfig.getTopicTraces())
        .onSuccess(v -> {
          log.info("[ARCHIVER-TRACES] Subscribed to {} bootstrapServers={}",
              kafkaConfig.getTopicTraces(), kafkaConfig.getBootstrapServers());
          flushTimerId = vertx.setPeriodic(archiverConfig.getFlushIntervalMs(),
              id -> sink.flushIfDue());
          startPromise.complete();
        })
        .onFailure(err -> {
          log.error("[ARCHIVER-TRACES] Failed to subscribe: {}", err.getMessage());
          startPromise.fail(err);
        });
  }

  private void maybeCommitKafkaOffsets(String topic) {
    Map<Integer, Long> minOffsets =
        KafkaCommittedOffsetAggregator.mergeAcrossWriters(sink.allWriters());
    if (minOffsets.isEmpty()) {
      return;
    }
    Map<TopicPartition, OffsetAndMetadata> toCommit = new HashMap<>();
    for (Map.Entry<Integer, Long> e : minOffsets.entrySet()) {
      toCommit.put(new TopicPartition(topic, e.getKey()),
          new OffsetAndMetadata().setOffset(e.getValue() + 1));
    }
    consumer.commit(toCommit)
        .onSuccess(v -> log.debug("[ARCHIVER-TRACES] Committed Kafka offsets: {}", minOffsets))
        .onFailure(err -> log.error("[ARCHIVER-TRACES] Kafka commit failed: {}", err.getMessage()));
  }

  @Override
  public void stop() {
    if (flushTimerId >= 0) {
      vertx.cancelTimer(flushTimerId);
    }
    try {
      sink.flush();
    } catch (IOException e) {
      log.warn("[ARCHIVER-TRACES] Final flush on stop failed: {}", e.getMessage());
    }
    if (consumer != null) {
      consumer.close();
    }
  }

  private static Map<String, String> buildKafkaConfig(KafkaConfig cfg) {
    Map<String, String> map = new HashMap<>();
    map.put("bootstrap.servers", cfg.getBootstrapServers());
    map.put("group.id", cfg.getGroupId());
    map.put("client.id", cfg.getClientId() + "-traces");
    map.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    map.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    map.put("auto.offset.reset", cfg.getAutoOffsetReset());
    map.put("enable.auto.commit", "false");
    map.put("max.poll.records", String.valueOf(cfg.getMaxPollRecords()));
    map.put("fetch.min.bytes", String.valueOf(cfg.getFetchMinBytes()));
    map.put("fetch.max.wait.ms", String.valueOf(cfg.getFetchMaxWaitMs()));
    return map;
  }

  private static Schema loadSchema(String resourcePath) {
    try (InputStream is = TracesConsumerVerticle.class.getClassLoader()
        .getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IllegalStateException("Schema not found: " + resourcePath);
      }
      return new Schema.Parser().parse(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load schema: " + resourcePath, e);
    }
  }

  private static String resolveNodeId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
