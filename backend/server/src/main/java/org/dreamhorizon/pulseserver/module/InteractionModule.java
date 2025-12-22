package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.impl.ConfigServiceImpl;
import org.dreamhorizon.pulseserver.service.interaction.ClickhouseMetricService;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricService;
import org.dreamhorizon.pulseserver.service.interaction.impl.InteractionServiceImpl;

public class InteractionModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(InteractionService.class).to(
        InteractionServiceImpl.class);
    bind(PerformanceMetricService.class).to(ClickhouseMetricService.class)
        .in(Singleton.class);
    bind(ConfigService.class).to(ConfigServiceImpl.class)
        .in(Singleton.class);
  }
}
