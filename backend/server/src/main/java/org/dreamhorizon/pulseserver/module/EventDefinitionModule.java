package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.service.eventdefinition.EventDefinitionService;
import org.dreamhorizon.pulseserver.service.eventdefinition.impl.EventDefinitionServiceImpl;

public class EventDefinitionModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(EventDefinitionService.class).to(EventDefinitionServiceImpl.class).in(Singleton.class);
  }
}
