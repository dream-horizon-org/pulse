package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.EventCatalogDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.FunnelResultsDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.JourneyResultsDao;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.impl.ConfigServiceImpl;
import org.dreamhorizon.pulseserver.service.interaction.ClickhouseMetricService;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricService;
import org.dreamhorizon.pulseserver.service.interaction.impl.InteractionServiceImpl;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownService;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionService;
import org.dreamhorizon.pulseserver.service.productAnalysis.eventcatalog.EventCatalogService;
import org.dreamhorizon.pulseserver.service.productAnalysis.eventcatalog.impl.EventCatalogServiceImpl;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelService;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.impl.FunnelServiceImpl;
import org.dreamhorizon.pulseserver.service.productAnalysis.journey.JourneyService;
import org.dreamhorizon.pulseserver.service.productAnalysis.journey.impl.JourneyServiceImpl;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceServiceImpl;

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
    bind(FunnelJourneyTagDao.class).in(Singleton.class);
    bind(EventCatalogDao.class).in(Singleton.class);
    bind(EventCatalogService.class).to(EventCatalogServiceImpl.class).in(Singleton.class);
    bind(FunnelResultsDao.class).in(Singleton.class);
    bind(JourneyResultsDao.class).in(Singleton.class);
    bind(FunnelService.class).to(FunnelServiceImpl.class).in(Singleton.class);
    bind(JourneyDao.class).in(Singleton.class);
    bind(JourneyService.class).to(JourneyServiceImpl.class).in(Singleton.class);
    bind(org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchServiceImpl.class)
        .in(Singleton.class);
    bind(org.dreamhorizon.pulseserver.service.analytics.ClickHouseBatchServiceImpl.class)
        .in(Singleton.class);
    bind(org.dreamhorizon.pulseserver.service.analytics.ClickHouseComputeService.class)
        .in(Singleton.class);
    bind(org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchService.class).to(
        org.dreamhorizon.pulseserver.service.analytics.RoutingAnalyticsBatchService.class)
        .in(Singleton.class);
    bind(RootCauseCacheDao.class).in(Singleton.class);
    bind(RootCauseService.class).in(Singleton.class);
    bind(ScreenRcaService.class).in(Singleton.class);
    bind(ErrorAttributionService.class).in(Singleton.class);
    bind(ErrorAttributionDrillDownService.class).in(Singleton.class);
    bind(SessionEvidenceService.class).to(SessionEvidenceServiceImpl.class).in(Singleton.class);
  }
}
