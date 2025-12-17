package org.dreamhorizon.pulsealertscron.module;

import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.util.SharedDataUtils;
import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;

public class ConfigModule extends AbstractModule {

  private final Vertx vertx;

  public ConfigModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(ApplicationConfig.class).toProvider(() -> SharedDataUtils.get(vertx, ApplicationConfig.class));
  }
}
