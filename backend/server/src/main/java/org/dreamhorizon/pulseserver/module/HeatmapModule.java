package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.service.heatmap.HeatmapService;
import org.dreamhorizon.pulseserver.service.heatmap.HeatmapServiceImpl;

public class HeatmapModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(HeatmapService.class).to(HeatmapServiceImpl.class).in(Singleton.class);
  }
}
