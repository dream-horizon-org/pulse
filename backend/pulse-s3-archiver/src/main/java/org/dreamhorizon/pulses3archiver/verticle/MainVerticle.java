package org.dreamhorizon.pulses3archiver.verticle;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulses3archiver.config.ArchiverConfig;
import org.dreamhorizon.pulses3archiver.config.ConfigUtils;
import org.dreamhorizon.pulses3archiver.config.KafkaConfig;
import org.dreamhorizon.pulses3archiver.config.S3Config;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;
import org.dreamhorizon.pulses3archiver.util.SharedDataUtils;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  @Override
  public Completable rxStart() {
    return ConfigUtils.getConfigRetriever(vertx)
        .rxGetConfig()
        .doOnSuccess(config -> {
          ArchiverConfig archiverConfig =
              config.getJsonObject("archiver", new JsonObject()).mapTo(ArchiverConfig.class);
          KafkaConfig kafkaConfig =
              config.getJsonObject("kafka", new JsonObject()).mapTo(KafkaConfig.class);
          S3Config s3Config =
              config.getJsonObject("s3", new JsonObject()).mapTo(S3Config.class);
          WriterConfig writerConfig =
              config.getJsonObject("writer", new JsonObject()).mapTo(WriterConfig.class);

          SharedDataUtils.put(vertx.getDelegate(), archiverConfig);
          SharedDataUtils.put(vertx.getDelegate(), kafkaConfig);
          SharedDataUtils.put(vertx.getDelegate(), s3Config);
          SharedDataUtils.put(vertx.getDelegate(), writerConfig);

          log.info("[MainVerticle] Loaded config: kafka.bootstrapServers={} "
                  + "s3.rootBucket={} s3.region={} "
                  + "(keys: s3://<rootBucket>/<project-id>/<table>/year=.../day=.../*.parquet) "
                  + "writer.flushSizeBytes={} writer.flushAgeMs={}",
              kafkaConfig.getBootstrapServers(),
              s3Config.getRootBucket(),
              s3Config.getRegion(),
              writerConfig.getFlushSizeBytes(),
              writerConfig.getFlushAgeMs());
        })
        .ignoreElement()
        .andThen(
            Completable.mergeArray(
                vertx.rxDeployVerticle(TracesConsumerVerticle::new,
                    new DeploymentOptions().setInstances(1)).ignoreElement(),
                vertx.rxDeployVerticle(LogsConsumerVerticle::new,
                    new DeploymentOptions().setInstances(1)).ignoreElement(),
                vertx.rxDeployVerticle(MetricsConsumerVerticle::new,
                    new DeploymentOptions().setInstances(1)).ignoreElement()
            )
        )
        .doOnComplete(() -> log.info("[MainVerticle] All consumer verticles deployed"));
  }

  @Override
  public Completable rxStop() {
    log.info("[MainVerticle] Stopping");
    return Completable.complete();
  }
}
