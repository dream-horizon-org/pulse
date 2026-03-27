package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.dao.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.journey.JourneyDao;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.impl.ConfigServiceImpl;
import org.dreamhorizon.pulseserver.service.funnel.FunnelDefinitionService;
import org.dreamhorizon.pulseserver.service.funnel.impl.FunnelDefinitionServiceImpl;
import org.dreamhorizon.pulseserver.service.journey.JourneyService;
import org.dreamhorizon.pulseserver.service.journey.impl.JourneyServiceImpl;
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
    bind(FunnelDefinitionDao.class).in(Singleton.class);
    bind(FunnelDefinitionService.class).to(FunnelDefinitionServiceImpl.class).in(Singleton.class);
    bind(JourneyDao.class).in(Singleton.class);
    bind(JourneyService.class).to(JourneyServiceImpl.class).in(Singleton.class);
    bind(org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchService.class).to(org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchServiceImpl.class)
        .in(Singleton.class);
  }
}
