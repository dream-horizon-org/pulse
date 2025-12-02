package in.horizonos.pulseserver.module;

import com.google.inject.AbstractModule;
import in.horizonos.pulseserver.config.ApplicationConfig;
import in.horizonos.pulseserver.config.ClickhouseConfig;
import in.horizonos.pulseserver.vertx.SharedDataUtils;
import io.vertx.core.Vertx;

public class ConfigModule extends AbstractModule {

  private final Vertx vertx;

  public ConfigModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(ApplicationConfig.class).toProvider(() -> SharedDataUtils.get(vertx, ApplicationConfig.class));
    bind(ClickhouseConfig.class).toProvider(() -> SharedDataUtils.get(vertx, ClickhouseConfig.class));
  }
}
