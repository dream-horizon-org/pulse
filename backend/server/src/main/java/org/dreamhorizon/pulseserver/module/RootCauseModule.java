package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseAlgorithm;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;

public class RootCauseModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(RootCauseQueryBuilder.class);
    bind(RootCauseCacheDao.class);
    bind(RootCauseAlgorithm.class);
    bind(RootCauseService.class);
  }
}
