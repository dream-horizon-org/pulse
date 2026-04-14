package org.dreamhorizon.pulseserver.service.rca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.Vertx;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;
import org.dreamhorizon.pulseserver.service.rootcause.RcaRelatedHeatmapsMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;

/** Worker-side RCA pipeline: enrich → AI → merge heatmaps → MySQL cache. */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportProcessor {

  private static final String RCA_REPORT_PATH = "rca/report";
  private static final int ERR_MSG_MAX = 4000;

  private final Vertx vertx;
  private final RcaReportJobDao jobDao;
  private final RcaReportCacheDao rcaReportCacheDao;
  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final RootCauseConfig rootCauseConfig;
  private final RcaRelatedHeatmapsMerger rcaRelatedHeatmapsMerger;
  private final RcaReportEnrichmentService enrichmentService;
  private final AiUpstreamProxyExecutor upstream;

  public void enqueueProcess(
      final RcaReportJob job,
      final String requestBody,
      final boolean forceRootCauseRefresh,
      final String authorization,
      final String rawQuery) {
    vertx.executeBlocking(
        () -> {
          runPipeline(job, requestBody, forceRootCauseRefresh, authorization, rawQuery);
          return null;
        },
        false,
        ar -> {
          if (ar.failed()) {
            log.error("RCA job worker failed for {}", job.jobId(), ar.cause());
            jobDao
                .markFailed(
                    job.jobId(),
                    job.projectId(),
                    job.interactionName(),
                    job.date(),
                    truncateMessage(ar.cause().getMessage()))
                .subscribe(() -> {}, e -> log.warn("markFailed failed: {}", e.getMessage()));
          }
        });
  }

  private void runPipeline(
      final RcaReportJob job,
      final String requestBody,
      final boolean forceRootCauseRefresh,
      final String authorization,
      final String rawQuery) {
    try {
      log.info("RCA job {} starting pipeline", job.jobId());
      jobDao.updateStatus(job.jobId(), RcaJobStatus.PROCESSING).blockingAwait();

      RcaParsedReportBody parsed = parseJobRequest(job, requestBody, forceRootCauseRefresh);
      if (parsed == null) {
        log.warn("RCA job {} failed at parse step", job.jobId());
        return;
      }

      RcaEnrichmentOutcome enrichment =
          enrichmentService
              .enrichAsync(parsed, forceRootCauseRefresh)
              .toCompletableFuture()
              .join();

      String targetUrl = upstream.buildTargetUrl(RCA_REPORT_PATH, rawQuery);
      AiProxyUpstreamResult proxyResult =
          Single.fromCompletionStage(
                  upstream.executeProxy(
                      "POST", targetUrl, enrichment.body(), authorization, job.projectId()))
              .subscribeOn(Schedulers.io())
              .blockingGet();

      if (!AiProxyUpstreamResult.isSuccessfulBuffered(proxyResult)) {
        log.warn(
            "RCA job {} AI upstream error status={} body={}",
            job.jobId(),
            proxyResult.getStatusCode(),
            proxyResult.getBufferedBody());
        jobDao
            .markFailed(
                job.jobId(),
                job.projectId(),
                job.interactionName(),
                job.date(),
                truncateMessage(
                    extractUpstreamErrorMessage(
                        proxyResult.getStatusCode(), proxyResult.getBufferedBody())))
            .blockingAwait();
        return;
      }

      finalizeSuccessfulRcaProxyResult(proxyResult, enrichment, job).toCompletableFuture().join();
      jobDao
          .markCompleted(job.jobId(), job.projectId(), job.interactionName(), job.date())
          .blockingAwait();
    } catch (Exception e) {
      log.error("RCA job {} failed", job.jobId(), e);
      jobDao
          .markFailed(
              job.jobId(),
              job.projectId(),
              job.interactionName(),
              job.date(),
              truncateMessage(e.getMessage()))
          .blockingAwait();
    }
  }

  private RcaParsedReportBody parseJobRequest(
      final RcaReportJob job, final String requestBody, final boolean forceRootCauseRefresh) {
    try {
      JsonNode tree = objectMapper.readTree(requestBody);
      if (!(tree instanceof ObjectNode objectRoot)) {
        jobDao
            .markFailed(
                job.jobId(),
                job.projectId(),
                job.interactionName(),
                job.date(),
                "Request body must be a JSON object")
            .blockingAwait();
        return null;
      }
      return new RcaParsedReportBody(
          requestBody,
          objectRoot,
          job.projectId(),
          job.interactionName(),
          job.date(),
          forceRootCauseRefresh);
    } catch (Exception e) {
      jobDao
          .markFailed(
              job.jobId(),
              job.projectId(),
              job.interactionName(),
              job.date(),
              truncateMessage(e.getMessage()))
          .blockingAwait();
      return null;
    }
  }

  private CompletionStage<AiProxyUpstreamResult> finalizeSuccessfulRcaProxyResult(
      final AiProxyUpstreamResult result,
      final RcaEnrichmentOutcome enrichment,
      final RcaReportJob job) {
    if (!AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
      return CompletableFuture.completedFuture(result);
    }
    String body = result.getBufferedBody();
    if (enrichment.enrichmentOk()
        && enrichment.rootCause() != null
        && enrichment.rootCause().getSegments() != null
        && !enrichment.rootCause().getSegments().isEmpty()) {
      try {
        JsonNode tree = objectMapper.readTree(body);
        if (tree instanceof ObjectNode root) {
          RootCauseQueryBuilder.Window window =
              new RootCauseQueryBuilder.Window(
                  enrichment.anchorDate(),
                  rootCauseConfig.getLookbackDays(),
                  enrichment.windowEndExclusive());
          CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
          rootCauseService
              .fetchDistinctScreensForInteraction(
                  job.projectId(), job.interactionName(), window)
              .subscribeOn(Schedulers.io())
              .timeout(30, java.util.concurrent.TimeUnit.SECONDS)
              .subscribe(
                  screens -> {
                    try {
                      rcaRelatedHeatmapsMerger.mergeInto(
                          root, enrichment.rootCause().getSegments(), window, screens);
                      String merged = objectMapper.writeValueAsString(root);
                      persistBufferedRcaReport(merged, result, job)
                          .whenComplete(
                              (updated, err) -> {
                                if (err != null) {
                                  done.completeExceptionally(err);
                                } else {
                                  done.complete(updated);
                                }
                              });
                    } catch (Exception e) {
                      log.warn("Failed to merge RCA related heatmaps: {}", e.getMessage());
                      persistBufferedRcaReport(body, result, job)
                          .whenComplete(
                              (updated, err) -> {
                                if (err != null) {
                                  done.completeExceptionally(err);
                                } else {
                                  done.complete(updated);
                                }
                              });
                    }
                  },
                  err -> done.completeExceptionally(err));
          return done;
        }
      } catch (Exception e) {
        log.warn("Failed to merge RCA related heatmaps: {}", e.getMessage());
      }
    }
    return persistBufferedRcaReport(body, result, job);
  }

  private CompletionStage<AiProxyUpstreamResult> persistBufferedRcaReport(
      final String body, final AiProxyUpstreamResult result, final RcaReportJob job) {
    String withMeta = applyCacheMetadata(body, true, Instant.now());
    String mediaType = result.getMediaType();
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), mediaType, withMeta);
    Completable putOp =
        rcaReportCacheDao.put(
            job.projectId(),
            job.interactionName(),
            job.date(),
            updated.getBufferedBody());
    CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
    putOp.andThen(Single.just(updated)).subscribe(done::complete, done::completeExceptionally);
    return done;
  }

  private String applyCacheMetadata(final String body, final boolean cached, final Instant cachedAt) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode) {
        ObjectNode obj = (ObjectNode) node;
        obj.put("cached", cached);
        if (cachedAt != null) {
          obj.put("cachedAt", DateTimeFormatter.ISO_INSTANT.format(cachedAt));
        }
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception exception) {
      return body;
    }
  }

  /**
   * Extracts a human-readable error message from an upstream HTTP error response.
   * Tries to read {@code error} or {@code message} fields from the JSON body; falls back to a
   * generic status-code message so raw JSON never reaches the user-facing error field.
   */
  private String extractUpstreamErrorMessage(final int statusCode, final String body) {
    if (body != null && !body.isBlank()) {
      try {
        JsonNode node = objectMapper.readTree(body);
        for (String field : new String[]{"error", "message", "detail"}) {
          JsonNode candidate = node.get(field);
          if (candidate != null && candidate.isTextual()) {
            String text = candidate.asText().trim();
            if (!text.isEmpty()) {
              return text;
            }
          }
        }
      } catch (Exception ignored) {
        // body is not valid JSON — fall through
      }
    }
    return "AI service returned an error (HTTP " + statusCode + "). Please try again.";
  }

  private static String truncateMessage(final String message) {
    if (message == null) {
      return "Unknown error";
    }
    if (message.length() <= ERR_MSG_MAX) {
      return message;
    }
    return message.substring(0, ERR_MSG_MAX);
  }
}
