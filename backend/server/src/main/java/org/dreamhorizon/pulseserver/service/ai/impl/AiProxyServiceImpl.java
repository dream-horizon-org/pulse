package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.ScreenRcaNarrativeCacheDao;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rootcause.RcaRelatedHeatmapsMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;

/**
 * HTTP client implementation for forwarding requests to the Pulse AI service.
 * Returns {@link AiProxyUpstreamResult} (no JAX-RS types); the controller maps to
 * {@link jakarta.ws.rs.core.Response}.
 *
 * <p>POST {@code rca/report} is delegated to {@link RcaReportProxyHandler}; POST {@code
 * rca/screen-report} to {@link ScreenRcaNarrativeProxyHandler} when full collaborators are wired;
 * otherwise the request is forwarded unchanged (see two-arg constructor).
 */
@Slf4j
public class AiProxyServiceImpl implements AiProxyService {

  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final String RCA_REPORT_PATH = "rca/report";
  private static final String RCA_SCREEN_REPORT_PATH = "rca/screen-report";

  /**
   * Per-request upstream timeout. Aligns with {@link
   * org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyController} {@code @Timeout(120000)}.
   */
  public static final long AI_PROXY_UPSTREAM_TIMEOUT_MS = AiUpstreamProxyExecutor.UPSTREAM_TIMEOUT_MS;

  private final AiUpstreamProxyExecutor upstreamExecutor;
  private final RcaReportProxyHandler rcaReportProxyHandler;
  private final ScreenRcaNarrativeProxyHandler screenRcaNarrativeProxyHandler;

  @Inject
  public AiProxyServiceImpl(
      @Named(Constants.WEB_CLIENT_AI_PROXY) WebClient webClient,
      ApplicationConfig config,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao,
      ScreenRcaNarrativeCacheDao screenRcaNarrativeCacheDao,
      SessionEvidenceService sessionEvidenceService,
      RootCauseConfig rootCauseConfig,
      RcaRelatedHeatmapsMerger rcaRelatedHeatmapsMerger) {
    this(
        wiringForProduction(
            webClient,
            config,
            objectMapper,
            rootCauseService,
            rcaReportCacheDao,
            screenRcaNarrativeCacheDao,
            sessionEvidenceService,
            rootCauseConfig,
            rcaRelatedHeatmapsMerger));
  }

  /**
   * For unit tests and simple wiring: plain proxy only (no RCA enrichment or MySQL cache).
   */
  public AiProxyServiceImpl(WebClient webClient, String aiServiceUrl) {
    this(wiringForPlainProxy(webClient, aiServiceUrl));
  }

  /**
   * Package-private constructor for tests: inject mock {@link WebClient} and RCA collaborators.
   */
  AiProxyServiceImpl(
      WebClient webClient,
      String aiServiceUrl,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao,
      ScreenRcaNarrativeCacheDao screenRcaNarrativeCacheDao,
      SessionEvidenceService sessionEvidenceService) {
    this(
        wiringForFullPipeline(
            webClient,
            aiServiceUrl,
            objectMapper,
            rootCauseService,
            rcaReportCacheDao,
            screenRcaNarrativeCacheDao,
            sessionEvidenceService,
            RootCauseConfig.withDefaults(null),
            new RcaRelatedHeatmapsMerger(objectMapper)));
  }

  private AiProxyServiceImpl(AiProxyWiring wiring) {
    this.upstreamExecutor = wiring.upstreamExecutor;
    this.rcaReportProxyHandler = wiring.rcaReportProxyHandler;
    this.screenRcaNarrativeProxyHandler = wiring.screenRcaNarrativeProxyHandler;
    log.info("AI proxy service initialized → {}", upstreamExecutor.getAiServiceUrl());
  }

  private static AiProxyWiring wiringForProduction(
      WebClient webClient,
      ApplicationConfig config,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao,
      ScreenRcaNarrativeCacheDao screenRcaNarrativeCacheDao,
      SessionEvidenceService sessionEvidenceService,
      RootCauseConfig rootCauseConfig,
      RcaRelatedHeatmapsMerger rcaRelatedHeatmapsMerger) {
    return wiringForFullPipeline(
        webClient,
        config.getAiServiceUrl(),
        objectMapper,
        rootCauseService,
        rcaReportCacheDao,
        screenRcaNarrativeCacheDao,
        sessionEvidenceService,
        rootCauseConfig,
        rcaRelatedHeatmapsMerger);
  }

  private static AiProxyWiring wiringForFullPipeline(
      WebClient webClient,
      String aiServiceUrl,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao,
      ScreenRcaNarrativeCacheDao screenRcaNarrativeCacheDao,
      SessionEvidenceService sessionEvidenceService,
      RootCauseConfig rootCauseConfig,
      RcaRelatedHeatmapsMerger rcaRelatedHeatmapsMerger) {
    String base = normalizeAiServiceUrl(aiServiceUrl);
    AiUpstreamProxyExecutor executor = new AiUpstreamProxyExecutor(webClient, base);
    RcaReportProxyHandler rcaHandler =
        new RcaReportProxyHandler(executor, objectMapper, rootCauseService, rcaReportCacheDao,
            rootCauseConfig, rcaRelatedHeatmapsMerger,
            sessionEvidenceService);
    ScreenRcaNarrativeProxyHandler screenHandler =
        new ScreenRcaNarrativeProxyHandler(executor, objectMapper, screenRcaNarrativeCacheDao);
    return new AiProxyWiring(executor, rcaHandler, screenHandler);
  }

  private static AiProxyWiring wiringForPlainProxy(WebClient webClient, String aiServiceUrl) {
    AiUpstreamProxyExecutor executor =
        new AiUpstreamProxyExecutor(webClient, normalizeAiServiceUrl(aiServiceUrl));
    return new AiProxyWiring(executor, null, null);
  }

  private static String normalizeAiServiceUrl(String url) {
    return url != null && !url.isBlank() ? url : DEFAULT_AI_SERVICE_URL;
  }

  // TODO: Refactor this as it violates the Single Responsibility Principle
  @Override
  public CompletionStage<AiProxyUpstreamResult> proxy(
      String method,
      String path,
      String rawQuery,
      String body,
      String authorization,
      String projectId) {
    boolean isRcaReportPost = "POST".equals(method) && RCA_REPORT_PATH.equals(path);
    if (isRcaReportPost && rcaReportProxyHandler != null) {
      return rcaReportProxyHandler.handlePost(rawQuery, body, authorization, projectId);
    }
    boolean isScreenRcaNarrativePost =
        "POST".equals(method) && RCA_SCREEN_REPORT_PATH.equals(path);
    if (isScreenRcaNarrativePost && screenRcaNarrativeProxyHandler != null) {
      return screenRcaNarrativeProxyHandler.handlePost(rawQuery, body, authorization, projectId);
    }
    String targetUrl = upstreamExecutor.buildTargetUrl(path, rawQuery);
    return upstreamExecutor.executeProxy(method, targetUrl, body, authorization, projectId);
  }

  private static final class AiProxyWiring {
    private final AiUpstreamProxyExecutor upstreamExecutor;
    private final RcaReportProxyHandler rcaReportProxyHandler;
    private final ScreenRcaNarrativeProxyHandler screenRcaNarrativeProxyHandler;

    private AiProxyWiring(
        AiUpstreamProxyExecutor upstreamExecutor,
        RcaReportProxyHandler rcaReportProxyHandler,
        ScreenRcaNarrativeProxyHandler screenRcaNarrativeProxyHandler) {
      this.upstreamExecutor = upstreamExecutor;
      this.rcaReportProxyHandler = rcaReportProxyHandler;
      this.screenRcaNarrativeProxyHandler = screenRcaNarrativeProxyHandler;
    }
  }
}
