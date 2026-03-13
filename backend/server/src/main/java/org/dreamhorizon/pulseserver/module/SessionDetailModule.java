package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.dao.sessiondetail.SessionDetailDao;
import org.dreamhorizon.pulseserver.service.sessiondetail.SessionDetailService;
import org.dreamhorizon.pulseserver.service.sessiondetail.impl.SessionDetailServiceImpl;

public class SessionDetailModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SessionDetailService.class).to(SessionDetailServiceImpl.class).in(Singleton.class);
    bind(SessionDetailDao.class).in(Singleton.class);
  }
}
