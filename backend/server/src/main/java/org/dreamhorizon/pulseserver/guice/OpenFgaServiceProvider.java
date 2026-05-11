package org.dreamhorizon.pulseserver.guice;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

/**
 * Builds {@link OpenFgaService} when OpenFGA is enabled, with DAOs for superadmin listing fallbacks.
 * Returns {@code null} when OpenFGA is disabled (same contract as previous inline provider).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OpenFgaServiceProvider implements Provider<OpenFgaService> {

  private final Vertx vertx;
  private final TenantDao tenantDao;
  private final ProjectDao projectDao;

  @Override
  public OpenFgaService get() {
    OpenFgaConfig config = SharedDataUtils.get(vertx, OpenFgaConfig.class);
    if (config == null || !config.isEnabled()) {
      return null;
    }
    try {
      return new OpenFgaService(config, tenantDao, projectDao);
    } catch (Exception e) {
      log.error("Failed to initialize OpenFgaService: {}", e.getMessage());
      return null;
    }
  }
}
