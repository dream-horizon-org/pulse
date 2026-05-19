package org.dreamhorizon.pulseserver.service.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.sqlclient.DatabaseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightExecutionMode;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobDao;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobKey;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobStatus;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightjob.models.InsightJob;
import org.dreamhorizon.pulseserver.dao.insightreport.InsightReportCacheDao;
import org.dreamhorizon.pulseserver.dao.insightreport.models.InsightReportCacheHit;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.insight.models.GetInsightJobResponse;

/** Creates and deduplicates insight async jobs; maps DB rows to poll API responses. */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightJobService {

  private static final String POLL_PATH_PREFIX = "/v1/insight/job/";

  private final InsightJobDao jobDao;
  private final InsightReportCacheDao cacheDao;
  private final ObjectMapper objectMapper;

  public Single<InsightJobDispatch> createOrGetJob(
      final InsightCacheKey key, final String createdBy) {
    if (key.regenerate()) {
      return insertNewJobWithDedup(key, createdBy)
          .doOnError(
              e ->
                  log.error(
                      "Insight createOrGetJob (regenerate) failed project={} type={} entity={}",
                      key.projectId(), key.insightType(), key.entityKey(), e));
    }
    return jobDao
        .getActiveJobByKey(
            key.projectId(), key.insightType(), key.entityKey(),
            key.executionMode(), key.startDate(), key.endDate())
        .map(job -> new InsightJobDispatch(job, false, null, false))
        .switchIfEmpty(Maybe.defer(() -> insertNewJobWithDedup(key, createdBy).toMaybe()))
        .toSingle()
        .doOnError(
            e ->
                log.error(
                    "Insight createOrGetJob failed project={} type={} entity={}",
                    key.projectId(), key.insightType(), key.entityKey(), e));
  }

  private Single<InsightJobDispatch> insertNewJobWithDedup(
      final InsightCacheKey key, final String createdBy) {
    String jobId = "insight-job-" + UUID.randomUUID();
    return jobDao
        .createJob(
            jobId,
            new InsightJobKey(
                key.projectId(), key.insightType(), key.entityKey(),
                key.executionMode(), key.startDate(), key.endDate()),
            createdBy)
        .map(job -> new InsightJobDispatch(job, true, key.requestBody(), key.regenerate()))
        .onErrorResumeNext(
            err -> {
              if (!isDuplicateKey(err)) {
                log.error(
                    "Insight job insert failed (non-duplicate) project={} type={} entity={}",
                    key.projectId(), key.insightType(), key.entityKey(), err);
                return Single.error(err);
              }
              log.debug(
                  "Insight job insert deduped for key {} {} {}",
                  key.projectId(), key.insightType(), key.entityKey());
              return jobDao
                  .getActiveJobByKey(
                      key.projectId(), key.insightType(), key.entityKey(),
                      key.executionMode(), key.startDate(), key.endDate())
                  .map(job -> new InsightJobDispatch(job, false, null, false))
                  .switchIfEmpty(
                      Maybe.error(
                          new IllegalStateException(
                              "Insight job insert conflict but no active job; please retry")))
                  .toSingle();
            });
  }

  /**
   * Read-only status check. Returns COMPLETED (with report) when the MySQL cache has a hit
   * (DATE_RANGE only), or the active job info when PENDING/PROCESSING, or empty when neither.
   */
  public Maybe<GetInsightJobResponse> peekStatus(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final InsightExecutionMode executionMode,
      final LocalDate startDate,
      final LocalDate endDate) {
    if (executionMode == InsightExecutionMode.DATE_RANGE) {
      return cacheDao
          .get(projectId, insightType, entityKey, executionMode, startDate, endDate)
          .map(this::buildCacheHitResponse)
          .switchIfEmpty(
              Maybe.defer(
                  () ->
                      jobDao
                          .getActiveJobByKey(
                              projectId, insightType, entityKey, executionMode,
                              startDate, endDate)
                          .map(job -> toResponse(job, null))));
    }
    return jobDao
        .getActiveJobByKey(projectId, insightType, entityKey, executionMode, startDate, endDate)
        .map(job -> toResponse(job, null));
  }

  public Single<GetInsightJobResponse> getJobStatus(
      final String jobId, final String projectIdHeader) {
    return jobDao
        .getJobById(jobId)
        .switchIfEmpty(Maybe.error(ServiceError.NOT_FOUND.getException()))
        .toSingle()
        .flatMap(
            job -> {
              if (projectIdHeader == null
                  || projectIdHeader.isBlank()
                  || !projectIdHeader.equals(job.projectId())) {
                return Single.error(ServiceError.NOT_FOUND.getException());
              }
              if (job.status() == InsightJobStatus.COMPLETED) {
                return cacheDao
                    .getFromWriterPool(
                        job.projectId(), job.insightType(), job.entityKey(),
                        job.executionMode(), job.startDate(), job.endDate())
                    .map(hit -> toResponse(job, hit))
                    .defaultIfEmpty(toResponse(job, null));
              }
              return Single.just(toResponse(job, null));
            });
  }

  private GetInsightJobResponse buildCacheHitResponse(final InsightReportCacheHit cacheHit) {
    JsonNode reportNode = null;
    Instant cachedAt = null;
    if (cacheHit.reportBody() != null && !cacheHit.reportBody().isBlank()) {
      try {
        JsonNode fullNode = objectMapper.readTree(cacheHit.reportBody());
        reportNode = extractReportField(fullNode);
        cachedAt = cacheHit.cachedAt();
      } catch (Exception e) {
        log.warn("Failed to parse cached insight report for peek: {}", e.getMessage());
      }
    }
    return GetInsightJobResponse.builder()
        .status(InsightJobStatus.COMPLETED.name())
        .report(reportNode)
        .cached(reportNode != null ? Boolean.TRUE : null)
        .cachedAt(cachedAt)
        .build();
  }

  private GetInsightJobResponse toResponse(
      final InsightJob job, final InsightReportCacheHit cacheHit) {
    JsonNode reportNode = null;
    Instant cachedAt = null;
    if (cacheHit != null && cacheHit.reportBody() != null && !cacheHit.reportBody().isBlank()) {
      try {
        JsonNode fullNode = objectMapper.readTree(cacheHit.reportBody());
        reportNode = extractReportField(fullNode);
        cachedAt = cacheHit.cachedAt();
      } catch (Exception e) {
        log.warn("Failed to parse cached insight report for job {}: {}", job.jobId(), e.getMessage());
      }
    }
    Boolean cached =
        job.status() == InsightJobStatus.COMPLETED && reportNode != null ? Boolean.TRUE : null;
    return GetInsightJobResponse.builder()
        .jobId(job.jobId())
        .insightType(job.insightType().name())
        .executionMode(job.executionMode().name())
        .status(job.status().name())
        .pollUrl(POLL_PATH_PREFIX + job.jobId())
        .createdAt(job.createdAt())
        .startedAt(job.startedAt())
        .completedAt(job.completedAt())
        .report(reportNode)
        .errorMessage(job.errorMessage())
        .cached(cached)
        .cachedAt(cachedAt)
        .build();
  }

  private static JsonNode extractReportField(final JsonNode fullNode) {
    JsonNode inner = fullNode.path("report");
    return inner.isMissingNode() || inner.isNull() ? fullNode : inner;
  }

  static boolean isDuplicateKey(final Throwable err) {
    Throwable t = err;
    while (t != null) {
      if (t instanceof DatabaseException db && db.getErrorCode() == 1062) {
        return true;
      }
      Throwable cause = t.getCause();
      if (cause == t) {
        break;
      }
      t = cause;
    }
    return false;
  }
}
