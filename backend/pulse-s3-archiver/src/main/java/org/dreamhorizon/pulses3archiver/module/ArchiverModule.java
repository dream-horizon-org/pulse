package org.dreamhorizon.pulses3archiver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulses3archiver.config.ArchiverConfig;
import org.dreamhorizon.pulses3archiver.config.KafkaConfig;
import org.dreamhorizon.pulses3archiver.config.S3Config;
import org.dreamhorizon.pulses3archiver.config.WriterConfig;
import org.dreamhorizon.pulses3archiver.service.S3UploadService;
import org.dreamhorizon.pulses3archiver.util.SharedDataUtils;

public class ArchiverModule extends AbstractModule {

  private final Vertx vertx;

  public ArchiverModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(Vertx.class).toInstance(this.vertx);
    bind(io.vertx.rxjava3.core.Vertx.class)
        .toInstance(io.vertx.rxjava3.core.Vertx.newInstance(vertx));

    bind(ArchiverConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, ArchiverConfig.class));
    bind(KafkaConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, KafkaConfig.class));
    bind(S3Config.class)
        .toProvider(() -> SharedDataUtils.get(vertx, S3Config.class));
    bind(WriterConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, WriterConfig.class));

    bind(S3UploadService.class).in(Singleton.class);
  }
}
