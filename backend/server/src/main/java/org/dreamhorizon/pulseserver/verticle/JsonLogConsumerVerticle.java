package org.dreamhorizon.pulseserver.verticle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.errorgrouping.service.ErrorGroupingService;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.model.VectorLogRecord;

@Slf4j
public class JsonLogConsumerVerticle extends AbstractVerticle {

  private static final int BATCH_SIZE = 1000;
  private static final long FLUSH_INTERVAL_MS = 2000;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<VectorLogRecord> buffer = new ArrayList<>();

  private KafkaConsumer<String, String> consumer;
  private long flushTimerId = -1;

  @Override
  public void start(Promise<Void> startPromise) {

    String bootstrapServers =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094");
    String topic = "pulse.logs.anr_crash";

    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", bootstrapServers);
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("group.id", "pulse-anr-crash-consumer");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "true");

    consumer = KafkaConsumer.create(vertx, config);

    ErrorGroupingService errorGroupingService =
        GuiceInjector.getGuiceInjector().getInstance(ErrorGroupingService.class);

    consumer.handler(record -> {
      String value = record.value();
      if (value == null || value.isBlank()) {
        return;
      }

      try {
        VectorLogRecord logRecord =
            objectMapper.readValue(value, VectorLogRecord.class);

        buffer.add(logRecord);

        if (buffer.size() >= BATCH_SIZE) {
          flush(errorGroupingService);
        }
      } catch (Exception e) {
        // you explicitly don’t care if a record fails
        log.warn("Failed to parse log record, skipping", e);
      }
    });

    consumer.subscribe(topic)
        .onSuccess(v -> {
          log.info("Subscribed to topic {}", topic);

          flushTimerId = vertx.setPeriodic(FLUSH_INTERVAL_MS, id -> {
            if (!buffer.isEmpty()) {
              flush(errorGroupingService);
            }
          });

          startPromise.complete();
        })
        .onFailure(err -> {
          log.error("Failed to subscribe", err);
          startPromise.fail(err);
        });
  }

  private void flush(ErrorGroupingService errorGroupingService) {
    List<VectorLogRecord> batch = new ArrayList<>(buffer);
    buffer.clear();

    errorGroupingService.ingest(batch)
        .subscribe(
            count -> log.info("Ingested {} records", count),
            err -> log.warn("Batch ingest failed, ignoring", err)
        );
  }

  @Override
  public void stop() {
    if (flushTimerId != -1) {
      vertx.cancelTimer(flushTimerId);
    }
    if (consumer != null) {
      consumer.close();
    }
  }
}
