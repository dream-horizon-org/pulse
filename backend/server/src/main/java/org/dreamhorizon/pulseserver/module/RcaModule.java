package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;
import org.dreamhorizon.pulseserver.service.rca.RcaReportEnrichmentService;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;
import org.dreamhorizon.pulseserver.service.rca.RcaReportProcessor;

/** RCA async jobs, enrichment, and shared {@link AiUpstreamProxyExecutor} for proxy + worker. */
public class RcaModule extends AbstractModule {

  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";

  @Override
  protected void configure() {
    bind(RcaReportJobDao.class).in(Singleton.class);
    bind(RcaReportJobService.class).in(Singleton.class);
    bind(RcaReportEnrichmentService.class).in(Singleton.class);
    bind(RcaReportProcessor.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  static AiUpstreamProxyExecutor provideAiUpstreamProxyExecutor(
      @Named(Constants.WEB_CLIENT_AI_PROXY) WebClient webClient, ApplicationConfig config) {
    String url = config.getAiServiceUrl();
    if (url == null || url.isBlank()) {
      url = DEFAULT_AI_SERVICE_URL;
    }
    return new AiUpstreamProxyExecutor(webClient, url);
  }
}
