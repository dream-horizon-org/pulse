package org.dreamhorizon.pulseserver.verticle;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.errorgrouping.service.ErrorGroupingService;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;

@Slf4j
public class AnrCrashLogConsumerVerticle extends AbstractVerticle {

  private Disposable subscription;

  @Override
  public void start(Promise<Void> startPromise) {
    // Inside Docker: kafka:9092
    // From local dev: override via env KAFKA_BOOTSTRAP_SERVERS=localhost:9094
    String bootstrapServers =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    String topic = "pulse.logs.anr_crash";

    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", bootstrapServers);
    config.put("key.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer",
        "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    config.put("group.id", "pulse-anr-crash-consumer");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "true");

    KafkaConsumer<String, byte[]> consumer = KafkaConsumer.create(vertx, config);

    // Flowable of ExportLogsServiceRequest coming from Kafka
    Flowable<ExportLogsServiceRequest> requestStream =
        Flowable.create(emitter -> {
          consumer.handler(record -> {
            byte[] value = record.value();
            if (value == null || value.length == 0) {
              return;
            }
            try {
              ExportLogsServiceRequest req =
                  ExportLogsServiceRequest.parseFrom(value);
              emitter.onNext(req);
            } catch (Exception e) {
              log.error("[ANR-CONSUMER] Failed to parse payload: {}", e.getMessage());
            }
          });

          consumer.subscribe(topic)
              .onSuccess(v -> {
                log.info("[ANR-CONSUMER] Subscribed to {} (bootstrapServers={})", topic, bootstrapServers);
                emitter.setCancellable(() -> {
                  log.info("[ANR-CONSUMER] Cancelling stream, closing Kafka consumer");
                  consumer.close();
                });
                startPromise.complete();
              })
              .onFailure(err -> {
                log.error("[ANR-CONSUMER] Failed to subscribe: {}", err.getMessage());
                emitter.onError(err);
                startPromise.fail(err);
              });

        }, BackpressureStrategy.BUFFER);

    ErrorGroupingService errorGroupingService = GuiceInjector.getGuiceInjector().getInstance(ErrorGroupingService.class);

    Flowable<Long> logRecordStream =
        requestStream.flatMapSingle(errorGroupingService::ingest);
    subscription = logRecordStream.subscribe(
        records -> log.info("Ingested {} records", records),
        err -> log.error("[ANR-CONSUMER] Stream error: {}", String.valueOf(err)),
        () -> log.info("[ANR-CONSUMER] Stream completed")
    );
  }

  @Override
  public void stop() {
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
    }
  }
}