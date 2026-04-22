package org.dreamhorizon.pulseserver.dao.rcajob;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.Arrays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportJobDao {

  private final MysqlClient mysqlClient;

  public Single<RcaReportJob> createJob(
      final String jobId,
      final String projectId,
      final RcaType type,
      final String entityKey,
      final LocalDate date,
      final String createdBy) {
    return mysqlClient
        .getWriterPool()
        .preparedQuery(RcaReportJobQueries.INSERT_JOB)
        .rxExecute(
            Tuple.wrap(
                Arrays.asList(
                    jobId,
                    projectId,
                    type.name(),
                    entityKey,
                    date,
                    RcaJobStatus.PENDING.name(),
                    null,
                    createdBy,
                    null)))
        .ignoreElement()
        .andThen(
            Single.fromCallable(
                () ->
                    new RcaReportJob(
                        jobId,
                        projectId,
                        type,
                        entityKey,
                        date,
                        RcaJobStatus.PENDING,
                        null,
                        Instant.now(),
                        null,
                        null,
                        createdBy,
                        null)))
        .doOnError(e -> log.warn("RCA report job insert failed: {}", e.getMessage()));
  }

  public Maybe<RcaReportJob> getJobById(String jobId) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(RcaReportJobQueries.GET_JOB_BY_ID)
        .rxExecute(Tuple.of(jobId))
        .flatMapMaybe(
            rows -> {
              if (rows.size() == 0) {
                return Maybe.empty();
              }
              return Maybe.just(mapRow(rows.iterator().next()));
            })
        .doOnError(e -> log.warn("RCA report job get by id failed: {}", e.getMessage()));
  }

  public Maybe<RcaReportJob> getActiveJobByKey(
      String projectId, RcaType type, String entityKey, LocalDate date) {
    final String pending = RcaJobStatus.PENDING.name();
    final String processing = RcaJobStatus.PROCESSING.name();
    return mysqlClient
        .getReaderPool()
        .preparedQuery(RcaReportJobQueries.GET_ACTIVE_JOB_BY_KEY)
        .rxExecute(Tuple.of(
            projectId,
            type.name(),
            entityKey,
            java.sql.Date.valueOf(date),
            pending,
            processing,
            processing,
            pending))
        .flatMapMaybe(
            rows -> {
              if (rows.size() == 0) {
                return Maybe.empty();
              }
              return Maybe.just(mapRow(rows.iterator().next()));
            })
        .doOnError(e -> log.warn("RCA report job get active by key failed: {}", e.getMessage()));
  }

  public Completable updateStatus(String jobId, RcaJobStatus status) {
    final String processing = RcaJobStatus.PROCESSING.name();
    return mysqlClient
        .getWriterPool()
        .preparedQuery(RcaReportJobQueries.UPDATE_STATUS)
        .rxExecute(Tuple.of(status.name(), status.name(), processing, jobId))
        .ignoreElement()
        .doOnError(e -> log.warn("RCA report job update status failed: {}", e.getMessage()));
  }

  public Completable markCompleted(
      String jobId,
      String projectId,
      RcaType type,
      String entityKey,
      LocalDate date) {
    final String completed = RcaJobStatus.COMPLETED.name();
    Completable deleteOld =
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.DELETE_OLD_JOBS)
            .rxExecute(Tuple.of(
                projectId,
                type.name(),
                entityKey,
                java.sql.Date.valueOf(date),
                completed,
                jobId))
            .ignoreElement()
            .doOnError(e -> log.warn("RCA delete old completed row failed: {}", e.getMessage()))
            .onErrorComplete();
    return deleteOld.andThen(
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.FINALIZE_SUCCESS)
            .rxExecute(Tuple.of(completed, jobId))
            .ignoreElement()
            .doOnError(e -> log.warn("RCA report job mark completed failed: {}", e.getMessage())));
  }

  public Completable markFailed(
      String jobId,
      String projectId,
      RcaType type,
      String entityKey,
      LocalDate date,
      String errorMessage) {
    final String failed = RcaJobStatus.FAILED.name();
    Completable deleteOld =
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.DELETE_OLD_JOBS)
            .rxExecute(Tuple.of(
                projectId,
                type.name(),
                entityKey,
                java.sql.Date.valueOf(date),
                failed,
                jobId))
            .ignoreElement()
            .doOnError(e -> log.warn("RCA delete old failed row failed: {}", e.getMessage()))
            .onErrorComplete();
    return deleteOld.andThen(
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.FINALIZE_FAILURE)
            .rxExecute(Tuple.of(failed, errorMessage, jobId))
            .ignoreElement()
            .doOnError(
                e -> log.warn("RCA report job persist FAILED status failed: {}", e.getMessage())));
  }

  /** Marks PENDING/PROCESSING jobs older than {@code thresholdMinutes} as FAILED. */
  public Single<Integer> markStaleJobsFailed(final int thresholdMinutes) {
    final String pending = RcaJobStatus.PENDING.name();
    final String processing = RcaJobStatus.PROCESSING.name();
    final String failed = RcaJobStatus.FAILED.name();
    return mysqlClient
        .getWriterPool()
        .preparedQuery(RcaReportJobQueries.MARK_STALE_JOBS)
        .rxExecute(Tuple.of(failed, pending, processing, thresholdMinutes))
        .map(result -> result.rowCount())
        .doOnError(e -> log.warn("RCA stale job cleanup failed: {}", e.getMessage()));
  }

  private static RcaReportJob mapRow(Row row) {
    String typeStr = row.getString(2);
    RcaType type = RcaType.valueOf(typeStr);
    String statusStr = row.getString(5);
    RcaJobStatus status = RcaJobStatus.valueOf(statusStr);
    LocalDateTime created = row.getLocalDateTime(7);
    LocalDateTime started = row.getLocalDateTime(8);
    LocalDateTime completed = row.getLocalDateTime(9);
    return new RcaReportJob(
        row.getString(0),
        row.getString(1),
        type,
        row.getString(3),
        row.getLocalDate(4),
        status,
        row.getString(6),
        created != null ? created.toInstant(ZoneOffset.UTC) : null,
        started != null ? started.toInstant(ZoneOffset.UTC) : null,
        completed != null ? completed.toInstant(ZoneOffset.UTC) : null,
        row.getString(10),
        row.getString(11));
  }
}
