package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.dao.webvitals.WebVitalsDao;
import org.dreamhorizon.pulseserver.service.webvitals.WebVitalsService;
import org.dreamhorizon.pulseserver.service.webvitals.WebVitalsServiceImpl;

public class WebVitalsModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(WebVitalsDao.class).in(Singleton.class);
    bind(WebVitalsService.class).to(WebVitalsServiceImpl.class).in(Singleton.class);
  }
}
