package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;
import org.dreamhorizon.pulseserver.service.ai.impl.AiProxyServiceImpl;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceServiceImpl;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

public class ConfigModule extends AbstractModule {

  private final Vertx vertx;

  public ConfigModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(ApplicationConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, ApplicationConfig.class));
    bind(ClickhouseConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, ClickhouseConfig.class));
    bind(AthenaConfig.class).toProvider(() -> SharedDataUtils.get(vertx, AthenaConfig.class));
    bind(NotificationConfig.class)
        .toProvider(() -> SharedDataUtils.get(vertx, NotificationConfig.class));
    bind(SessionEvidenceService.class).to(SessionEvidenceServiceImpl.class).in(Singleton.class);
    bind(AiProxyService.class).to(AiProxyServiceImpl.class).in(Singleton.class);
  }
}
