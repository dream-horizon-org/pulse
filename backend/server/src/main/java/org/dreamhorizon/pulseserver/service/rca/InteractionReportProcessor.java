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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;

/** Worker for {@code INTERACTION_REPORT} jobs — calls pulse_ai stub until pipeline (issue 04) lands. */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InteractionReportProcessor {

  private static final String INTERACTION_REPORT_GENERATE_PATH = "interaction-report/generate";
  private static final int ERR_MSG_MAX = 4000;

  private final Vertx vertx;
  private final RcaReportJobDao jobDao;
  private final RcaReportCacheDao rcaReportCacheDao;
  private final ObjectMapper objectMapper;
  private final AiUpstreamProxyExecutor upstream;

  public void enqueueProcess(
      final RcaReportJob job,
      final String requestBody,
      final String authorization,
      final String rawQuery) {
    vertx.executeBlocking(
        () -> {
          runPipeline(job, requestBody, authorization, rawQuery).blockingAwait();
          return null;
        },
        false,
        ar -> {
          if (ar.failed()) {
            log.error("Interaction report job worker failed for {}", job.jobId(), ar.cause());
            markJobFailed(job, truncateMessage(ar.cause().getMessage()))
                .subscribe(() -> {}, e -> log.warn("markFailed failed: {}", e.getMessage()));
          }
        });
  }

  private Completable runPipeline(
      final RcaReportJob job,
      final String requestBody,
      final String authorization,
      final String rawQuery) {
    log.info("Interaction report job {} starting pipeline", job.jobId());
    String targetUrl = upstream.buildTargetUrl(INTERACTION_REPORT_GENERATE_PATH, rawQuery);
    return jobDao
        .updateStatus(job.jobId(), RcaJobStatus.PROCESSING)
        .andThen(
            Single.fromCompletionStage(
                upstream.executeProxy(
                    "POST", targetUrl, requestBody, authorization, job.projectId())))
        .flatMap(
            proxyResult -> {
              if (!AiProxyUpstreamResult.isSuccessfulBuffered(proxyResult)) {
                String errorMessage =
                    extractUpstreamErrorMessage(
                        proxyResult.getStatusCode(), proxyResult.getBufferedBody());
                return markJobFailed(job, truncateMessage(errorMessage))
                    .andThen(Single.error(new RuntimeException(errorMessage)));
              }
              return persistAndComplete(job, proxyResult);
            })
        .ignoreElement();
  }

  private Single<AiProxyUpstreamResult> persistAndComplete(
      final RcaReportJob job, final AiProxyUpstreamResult proxyResult) {
    String withMeta = applyCacheMetadata(proxyResult.getBufferedBody(), true, Instant.now());
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(
            proxyResult.getStatusCode(), proxyResult.getMediaType(), withMeta);
    return rcaReportCacheDao
        .put(
            job.projectId(),
            job.entityType(),
            job.entityKey(),
            job.date(),
            updated.getBufferedBody())
        .andThen(markJobCompleted(job))
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

  private String extractUpstreamErrorMessage(final int statusCode, final String body) {
    if (body != null && !body.isBlank()) {
      try {
        JsonNode node = objectMapper.readTree(body);
        for (String field : new String[] {"error", "message", "detail"}) {
          JsonNode candidate = node.get(field);
          if (candidate != null && candidate.isTextual()) {
            String text = candidate.asText().trim();
            if (!text.isEmpty()) {
              return text;
            }
          }
        }
      } catch (Exception ignored) {
        // fall through
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
