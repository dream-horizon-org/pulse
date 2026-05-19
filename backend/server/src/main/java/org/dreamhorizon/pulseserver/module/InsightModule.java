package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.dao.insightdayreport.InsightDayReportCacheDao;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobDao;
import org.dreamhorizon.pulseserver.dao.insightreport.InsightReportCacheDao;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.InsightDailySnapshotDao;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.InsightSnapshotDao;
import org.dreamhorizon.pulseserver.service.insight.DefaultInsightAgentResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightTypeDataFetcherResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightTypeSnapshotResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightAgentResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightDataFetcherResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightDateRangeResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightJobService;
import org.dreamhorizon.pulseserver.service.insight.InsightProcessor;
import org.dreamhorizon.pulseserver.service.insight.InsightSnapshotResolver;
import org.dreamhorizon.pulseserver.service.insight.InsightStaleJobCleanup;
import org.dreamhorizon.pulseserver.service.insight.anr.AnrDataFetcher;

public class InsightModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(InsightJobDao.class).in(Singleton.class);
    bind(InsightReportCacheDao.class).in(Singleton.class);
    bind(InsightDayReportCacheDao.class).in(Singleton.class);

    bind(InsightDailySnapshotDao.class).in(Singleton.class);
    bind(InsightSnapshotDao.class).to(InsightDailySnapshotDao.class).in(Singleton.class);

    // Per-type data fetchers — add new ones here as concrete singletons
    bind(AnrDataFetcher.class).in(Singleton.class);

    // Resolvers dispatch by InsightType — swap for custom impls when needed
    bind(InsightSnapshotResolver.class).to(InsightTypeSnapshotResolver.class).in(Singleton.class);
    bind(InsightDataFetcherResolver.class).to(InsightTypeDataFetcherResolver.class).in(Singleton.class);
    bind(InsightAgentResolver.class).to(DefaultInsightAgentResolver.class).in(Singleton.class);

    bind(InsightDateRangeResolver.class).in(Singleton.class);
    bind(InsightJobService.class).in(Singleton.class);
    bind(InsightProcessor.class).in(Singleton.class);
    bind(InsightStaleJobCleanup.class).in(Singleton.class);
  }
}
