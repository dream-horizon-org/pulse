package org.dreamhorizon.pulses3archiver.module;

import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulses3archiver.config.ArchiverConfig;
import org.dreamhorizon.pulses3archiver.util.SharedDataUtils;

public class ConfigModule extends AbstractModule {

  private final Vertx vertx;

  public ConfigModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(ArchiverConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, ArchiverConfig.class));
  }
}
