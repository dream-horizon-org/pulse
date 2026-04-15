package org.dreamhorizon.pulseserver.service.rca;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import io.vertx.sqlclient.DatabaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.ai.models.GetRcaJobResponse;

/** Creates and deduplicates RCA async jobs; maps DB rows to poll API responses. */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportJobService {

  private static final String POLL_PATH_PREFIX = "/v1/ai-rca/job/";

  private final RcaReportJobDao jobDao;
  private final RcaReportCacheDao cacheDao;
  private final ObjectMapper objectMapper;

  public Single<RcaJobDispatch> createOrGetJob(final RcaCacheKey key, final String createdBy) {
    // Regenerate always creates a new job — skip dedup so forceRootCauseRefresh is not lost.
    if (key.regenerate()) {
      return insertNewJobWithDedup(key, createdBy)
          .doOnError(
              e ->
                  log.error(
                      "RCA createOrGetJob (regenerate) failed project={} interaction={} date={}",
                      key.projectId(),
                      key.interactionName(),
                      key.date(),
                      e));
    }
    return jobDao
        .getActiveJobByKey(key.projectId(), key.interactionName(), key.date())
        .map(job -> new RcaJobDispatch(job, false, null, false))
        .switchIfEmpty(Maybe.defer(() -> insertNewJobWithDedup(key, createdBy).toMaybe()))
        .toSingle()
        .doOnError(
            e ->
                log.error(
                    "RCA createOrGetJob failed project={} interaction={} date={}",
                    key.projectId(),
                    key.interactionName(),
                    key.date(),
                    e));
  }

  private Single<RcaJobDispatch> insertNewJobWithDedup(
      final RcaCacheKey key, final String createdBy) {
    String jobId = "rca-job-" + UUID.randomUUID();
    return jobDao
        .createJob(jobId, key.projectId(), key.interactionName(), key.date(), createdBy)
        .map(job -> new RcaJobDispatch(job, true, key.requestBody(), key.regenerate()))
        .onErrorResumeNext(
            err -> {
              if (!isDuplicateKey(err)) {
                log.error(
                    "RCA job insert failed (non-duplicate) project={} interaction={} date={}",
                    key.projectId(),
                    key.interactionName(),
                    key.date(),
                    err);
                return Single.error(err);
              }
              log.debug("RCA job insert deduped for key {} {} {}", key.projectId(), key.interactionName(), key.date());
              return jobDao
                  .getActiveJobByKey(key.projectId(), key.interactionName(), key.date())
                  .map(job -> new RcaJobDispatch(job, false, null, false))
                  .switchIfEmpty(
                      Maybe.error(
                          new IllegalStateException(
                              "RCA job insert conflict but no active job; please retry")))
                  .toSingle();
            });
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

  /**
   * Read-only status check without triggering job creation.
   * Returns a COMPLETED response (with report) when the MySQL cache has a hit,
   * or the active job info when a PENDING/PROCESSING job exists, or empty when neither.
   */
  public Maybe<GetRcaJobResponse> peekStatus(
      final String projectId, final String interactionName, final LocalDate date) {
    return cacheDao
        .get(projectId, interactionName, date)
        .map(this::buildCacheHitResponse)
        .switchIfEmpty(
            Maybe.defer(
                () ->
                    jobDao
                        .getActiveJobByKey(projectId, interactionName, date)
                        .map(job -> toResponse(job, null))));
  }

  private GetRcaJobResponse buildCacheHitResponse(final RcaReportCacheHit cacheHit) {
    JsonNode reportNode = null;
    Instant cachedAt = null;
    if (cacheHit.reportBody() != null && !cacheHit.reportBody().isBlank()) {
      try {
        reportNode = objectMapper.readTree(cacheHit.reportBody());
        cachedAt = cacheHit.cachedAt();
      } catch (Exception e) {
        log.warn("Failed to parse cached RCA report for peek: {}", e.getMessage());
      }
    }
    return GetRcaJobResponse.builder()
        .status(RcaJobStatus.COMPLETED.name())
        .report(reportNode)
        .cached(reportNode != null ? Boolean.TRUE : null)
        .cachedAt(cachedAt)
        .build();
  }

  public Single<GetRcaJobResponse> getJobStatus(final String jobId, final String projectIdHeader) {
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
              if (job.status() == RcaJobStatus.COMPLETED) {
                // Read from the writer pool to avoid replication lag: the report was just written
                // to the primary, and a replica read could return empty before replication catches up.
                return cacheDao
                    .getFromWriterPool(job.projectId(), job.interactionName(), job.date())
                    .map(hit -> toResponse(job, hit))
                    .defaultIfEmpty(toResponse(job, null));
              }
              return Single.just(toResponse(job, null));
            });
  }

  private GetRcaJobResponse toResponse(final RcaReportJob job, final RcaReportCacheHit cacheHit) {
    JsonNode reportNode = null;
    Instant cachedAt = null;
    if (cacheHit != null && cacheHit.reportBody() != null && !cacheHit.reportBody().isBlank()) {
      try {
        reportNode = objectMapper.readTree(cacheHit.reportBody());
        cachedAt = cacheHit.cachedAt();
      } catch (Exception e) {
        log.warn("Failed to parse cached RCA report for job {}: {}", job.jobId(), e.getMessage());
      }
    }
    Boolean cached =
        job.status() == RcaJobStatus.COMPLETED && reportNode != null ? Boolean.TRUE : null;
    return GetRcaJobResponse.builder()
        .jobId(job.jobId())
        .status(job.status().name())
        .createdAt(job.createdAt())
        .startedAt(job.startedAt())
        .completedAt(job.completedAt())
        .report(reportNode)
        .errorMessage(job.errorMessage())
        .pollUrl(POLL_PATH_PREFIX + job.jobId())
        .cached(cached)
        .cachedAt(cachedAt)
        .build();
  }
}
