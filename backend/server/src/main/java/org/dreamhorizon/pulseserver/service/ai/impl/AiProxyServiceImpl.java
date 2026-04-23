package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;
import org.dreamhorizon.pulseserver.service.rca.RcaReportProcessor;

/**
 * HTTP client implementation for forwarding requests to the Pulse AI service.
 * Returns {@link AiProxyUpstreamResult} (no JAX-RS types); the controller maps to
 * {@link jakarta.ws.rs.core.Response}.
 *
 * <p>POST {@code rca/report} is delegated to {@link RcaReportProxyHandler} when full collaborators
 * are wired; otherwise the request is forwarded unchanged (see two-arg constructor).
 */
@Slf4j
public class AiProxyServiceImpl implements AiProxyService {

  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final String RCA_REPORT_PATH = "rca/report";

  /**
   * Per-request upstream timeout. Aligns with {@link
   * org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyController} {@code @Timeout(120000)}.
   */
  public static final long AI_PROXY_UPSTREAM_TIMEOUT_MS = AiUpstreamProxyExecutor.UPSTREAM_TIMEOUT_MS;

  private final AiUpstreamProxyExecutor upstreamExecutor;
  private final RcaReportProxyHandler rcaReportProxyHandler;

  @Inject
  public AiProxyServiceImpl(
      AiUpstreamProxyExecutor upstreamExecutor,
      ObjectMapper objectMapper,
      RcaReportCacheDao rcaReportCacheDao,
      RcaReportJobService rcaReportJobService,
      RcaReportProcessor rcaReportProcessor) {
    this.upstreamExecutor = upstreamExecutor;
    this.rcaReportProxyHandler =
        new RcaReportProxyHandler(
            objectMapper, rcaReportCacheDao, rcaReportJobService, rcaReportProcessor);
    log.info("AI proxy service initialized → {}", upstreamExecutor.getAiServiceUrl());
  }

  /**
   * For unit tests and simple wiring: plain proxy only (no RCA enrichment or MySQL cache).
   */
  public AiProxyServiceImpl(WebClient webClient, String aiServiceUrl) {
    this.upstreamExecutor =
        new AiUpstreamProxyExecutor(webClient, normalizeAiServiceUrl(aiServiceUrl));
    this.rcaReportProxyHandler = null;
    log.info("AI proxy service initialized → {}", upstreamExecutor.getAiServiceUrl());
  }

  /**
   * Package-private constructor for tests: inject mock {@link WebClient} and RCA collaborators.
   */
  AiProxyServiceImpl(
      WebClient webClient,
      String aiServiceUrl,
      ObjectMapper objectMapper,
      RcaReportCacheDao rcaReportCacheDao,
      RcaReportJobService rcaReportJobService,
      RcaReportProcessor rcaReportProcessor) {
    this(
        new AiUpstreamProxyExecutor(webClient, normalizeAiServiceUrl(aiServiceUrl)),
        objectMapper,
        rcaReportCacheDao,
        rcaReportJobService,
        rcaReportProcessor);
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
      String projectId,
      String createdByOrNull) {
    boolean isRcaReportPost = "POST".equals(method) && RCA_REPORT_PATH.equals(path);
    if (isRcaReportPost && rcaReportProxyHandler != null) {
      return rcaReportProxyHandler.handlePost(
          rawQuery, body, authorization, projectId, createdByOrNull);
    }
    String targetUrl = upstreamExecutor.buildTargetUrl(path, rawQuery);
    return upstreamExecutor.executeProxy(method, targetUrl, body, authorization, projectId);
  }
}
