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
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;
import org.dreamhorizon.pulseserver.service.rootcause.RcaRelatedHeatmapsMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/** Worker-side RCA pipeline: enrich → AI → merge heatmaps → MySQL cache. */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportProcessor {

  private static final String RCA_REPORT_PATH = "rca/report";
  private static final String RCA_SCREEN_REPORT_PATH = "rca/screen-report";
  private static final String RCA_SESSION_REPORT_PATH = "rca/session-report";
  private static final int ERR_MSG_MAX = 4000;
  private static final long HEATMAP_FETCH_TIMEOUT_SEC = 30;

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
          runPipeline(job, requestBody, forceRootCauseRefresh, authorization, rawQuery)
              .blockingAwait();
          return null;
        },
        false,
        ar -> {
          if (ar.failed()) {
            log.error("RCA job worker failed for {}", job.jobId(), ar.cause());
            markJobFailed(job, truncateMessage(ar.cause().getMessage()))
                .subscribe(() -> {}, e -> log.warn("markFailed failed: {}", e.getMessage()));
          }
        });
  }

  /**
   * Fully reactive RCA pipeline. All operations composed as RxJava chain with proper
   * error handling at each step. No blocking calls except the single blockingAwait()
   * inside vertx.executeBlocking() which is required by the worker pattern.
   */
  private Completable runPipeline(
      final RcaReportJob job,
      final String requestBody,
      final boolean forceRootCauseRefresh,
      final String authorization,
      final String rawQuery) {

    log.info("RCA job {} starting pipeline", job.jobId());

    return jobDao
        .updateStatus(job.jobId(), RcaJobStatus.PROCESSING)
        .andThen(Single.fromCallable(() -> parseJobRequest(job, requestBody, forceRootCauseRefresh)))
        .flatMap(parsed -> Single.fromCompletionStage(enrichmentService.enrichAsync(parsed, forceRootCauseRefresh)))
        .flatMap(
            enrichment -> {
              String aiPath;
              if (job.entityType() == RcaType.SCREEN) {
                aiPath = RCA_SCREEN_REPORT_PATH;
              } else if (job.entityType() == RcaType.SESSION) {
                aiPath = RCA_SESSION_REPORT_PATH;
              } else {
                aiPath = RCA_REPORT_PATH;
              }
              String targetUrl = upstream.buildTargetUrl(aiPath, rawQuery);
              return Single.fromCompletionStage(
                  upstream.executeProxy(
                      "POST", targetUrl, enrichment.body(), authorization, job.projectId()))
                  .map(proxyResult -> new EnrichmentWithResult(enrichment, proxyResult));
            })
        .flatMap(
            pair -> {
              AiProxyUpstreamResult proxyResult = pair.proxyResult();
              if (!AiProxyUpstreamResult.isSuccessfulBuffered(proxyResult)) {
                log.warn(
                    "RCA job {} AI upstream error status={} body={}",
                    job.jobId(),
                    proxyResult.getStatusCode(),
                    proxyResult.getBufferedBody());
                String errorMessage =
                    extractUpstreamErrorMessage(proxyResult.getStatusCode(), proxyResult.getBufferedBody());
                return markJobFailed(job, truncateMessage(errorMessage))
                    .andThen(Single.error(new RuntimeException("AI upstream error: " + errorMessage)));
              }
              return finalizeSuccessfulRcaProxyResult(pair.proxyResult(), pair.enrichment(), job)
                  .flatMap(r -> markJobCompleted(job).andThen(Single.defer(() -> Single.just(r))));
            })
        .ignoreElement()
        .onErrorResumeNext(
            error -> {
              log.error("RCA job {} failed", job.jobId(), error);
              // Only mark failed if not already marked in the flatMap error handler above
              String msg = error.getMessage();
              boolean alreadyMarked = msg != null && msg.contains("AI upstream error:");
              if (alreadyMarked) {
                return Completable.complete();
              }
              return markJobFailed(job, truncateMessage(msg));
            });
  }

  private RcaParsedReportBody parseJobRequest(
      final RcaReportJob job, final String requestBody, final boolean forceRootCauseRefresh) {
    JsonNode tree;
    try {
      tree = objectMapper.readTree(requestBody);
    } catch (Exception e) {
      throw new IllegalArgumentException("Malformed RCA request body: " + e.getMessage(), e);
    }
    if (!(tree instanceof ObjectNode objectRoot)) {
      throw new IllegalArgumentException("Request body must be a JSON object");
    }
    return new RcaParsedReportBody(
        requestBody,
        objectRoot,
        job.projectId(),
        job.entityType(),
        job.entityKey(),
        job.date(),
        forceRootCauseRefresh);
  }

  /**
   * Finalizes successful RCA result by merging heatmaps if applicable.
   * Fully reactive using RxJava operators.
   */
  private Single<AiProxyUpstreamResult> finalizeSuccessfulRcaProxyResult(
      final AiProxyUpstreamResult result,
      final RcaEnrichmentOutcome enrichment,
      final RcaReportJob job) {

    String body = result.getBufferedBody();

    boolean shouldMergeHeatmaps =
        job.entityType() != RcaType.SCREEN
            && job.entityType() != RcaType.SESSION
            && enrichment.enrichmentOk()
            && enrichment.rootCause() != null
            && enrichment.rootCause().getSegments() != null
            && !enrichment.rootCause().getSegments().isEmpty();

    if (!shouldMergeHeatmaps) {
      return persistBufferedRcaReport(body, result, job);
    }

    JsonNode tree;
    try {
      tree = objectMapper.readTree(body);
    } catch (Exception e) {
      log.warn("Failed to parse RCA response body for heatmap merging: {}", e.getMessage());
      return persistBufferedRcaReport(body, result, job);
    }

    if (!(tree instanceof ObjectNode root)) {
      return persistBufferedRcaReport(body, result, job);
    }

    RootCauseQueryBuilder.Window window =
        new RootCauseQueryBuilder.Window(
            enrichment.anchorDate(),
            rootCauseConfig.getLookbackDays(),
            enrichment.windowEndExclusive());

    List<RootCauseSegment> segments = enrichment.rootCause().getSegments();

    return rootCauseService
        .fetchDistinctScreensForInteraction(job.projectId(), job.entityKey(), window)
        .subscribeOn(Schedulers.io())
        .timeout(HEATMAP_FETCH_TIMEOUT_SEC, TimeUnit.SECONDS)
        .flatMap(
            screens -> {
              try {
                rcaRelatedHeatmapsMerger.mergeInto(root, segments, window, screens);
                String merged = objectMapper.writeValueAsString(root);
                return persistBufferedRcaReport(merged, result, job);
              } catch (Exception e) {
                log.warn("Failed to merge RCA related heatmaps: {}", e.getMessage());
                return persistBufferedRcaReport(body, result, job);
              }
            })
        .onErrorResumeNext(
            error -> {
              log.warn("Failed to fetch screens for heatmap merging: {}", error.getMessage());
              return persistBufferedRcaReport(body, result, job);
            });
  }

  private Single<AiProxyUpstreamResult> persistBufferedRcaReport(
      final String body, final AiProxyUpstreamResult result, final RcaReportJob job) {
    String withMeta = applyCacheMetadata(body, true, Instant.now());
    String mediaType = result.getMediaType();
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), mediaType, withMeta);

    return rcaReportCacheDao
        .put(
            job.projectId(),
            job.entityType(),
            job.entityKey(),
            job.date(),
            updated.getBufferedBody())
        .andThen(Single.just(updated));
  }

  private Completable markJobCompleted(final RcaReportJob job) {
    return jobDao.markCompleted(
        job.jobId(), job.projectId(), job.entityType(), job.entityKey(), job.date());
  }

  private Completable markJobFailed(final RcaReportJob job, final String errorMessage) {
    return jobDao.markFailed(
        job.jobId(),
        job.projectId(),
        job.entityType(),
        job.entityKey(),
        job.date(),
        errorMessage);
  }

  private String applyCacheMetadata(final String body, final boolean cached, final Instant cachedAt) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode obj) {
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

  private record EnrichmentWithResult(RcaEnrichmentOutcome enrichment, AiProxyUpstreamResult proxyResult) {}
}
