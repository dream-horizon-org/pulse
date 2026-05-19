package org.dreamhorizon.pulseserver.dao.insightjob;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlResult;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.insightjob.models.InsightJob;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightJobDao {

  private final MysqlClient mysqlClient;

  public Single<InsightJob> createJob(
      final String jobId,
      final InsightJobKey key,
      final String createdBy) {
    return mysqlClient
        .getWriterPool()
        .preparedQuery(InsightJobQueries.INSERT_JOB)
        .rxExecute(
            Tuple.wrap(
                Arrays.asList(
                    jobId,
                    key.projectId(),
                    key.insightType().name(),
                    key.entityKey(),
                    key.executionMode().name(),
                    key.startDate() != null ? java.sql.Date.valueOf(key.startDate()) : null,
                    key.endDate() != null ? java.sql.Date.valueOf(key.endDate()) : null,
                    InsightJobStatus.PENDING.name(),
                    null,
                    createdBy)))
        .ignoreElement()
        .andThen(
            Single.fromCallable(
                () ->
                    InsightJob.builder()
                        .jobId(jobId)
                        .projectId(key.projectId())
                        .insightType(key.insightType())
                        .entityKey(key.entityKey())
                        .executionMode(key.executionMode())
                        .startDate(key.startDate())
                        .endDate(key.endDate())
                        .status(InsightJobStatus.PENDING)
                        .errorMessage(null)
                        .createdAt(Instant.now())
                        .startedAt(null)
                        .completedAt(null)
                        .createdBy(createdBy)
                        .build()))
        .doOnError(e -> log.warn("Insight job insert failed: {}", e.getMessage()));
  }

  public Maybe<InsightJob> getJobById(final String jobId) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(InsightJobQueries.GET_JOB_BY_ID)
        .rxExecute(Tuple.of(jobId))
        .flatMapMaybe(
            rows -> {
              if (rows.size() == 0) {
                return Maybe.empty();
              }
              return Maybe.just(mapRow(rows.iterator().next()));
            })
        .doOnError(e -> log.warn("Insight job get by id failed: {}", e.getMessage()));
  }

  public Maybe<InsightJob> getActiveJobByKey(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final InsightExecutionMode executionMode,
      final LocalDate startDate,
      final LocalDate endDate) {
    final String pending = InsightJobStatus.PENDING.name();
    final String processing = InsightJobStatus.PROCESSING.name();
    return mysqlClient
        .getReaderPool()
        .preparedQuery(InsightJobQueries.GET_ACTIVE_JOB_BY_KEY)
        .rxExecute(
            Tuple.wrap(
                new Object[] {
                  projectId,
                  insightType.name(),
                  entityKey,
                  executionMode.name(),
                  startDate != null ? java.sql.Date.valueOf(startDate) : null,
                  endDate != null ? java.sql.Date.valueOf(endDate) : null,
                  pending,
                  processing,
                  processing,
                  pending
                }))
        .flatMapMaybe(
            rows -> {
              if (rows.size() == 0) {
                return Maybe.empty();
              }
              return Maybe.just(mapRow(rows.iterator().next()));
            })
        .doOnError(e -> log.warn("Insight job get active by key failed: {}", e.getMessage()));
  }

  public Completable updateStatus(final String jobId, final InsightJobStatus status) {
    final String processing = InsightJobStatus.PROCESSING.name();
    return mysqlClient
        .getWriterPool()
        .preparedQuery(InsightJobQueries.UPDATE_STATUS)
        .rxExecute(Tuple.of(status.name(), status.name(), processing, jobId))
        .ignoreElement()
        .doOnError(e -> log.warn("Insight job update status failed: {}", e.getMessage()));
  }

  public Completable markCompleted(
      final String jobId,
      final InsightJobKey key) {
    final String completed = InsightJobStatus.COMPLETED.name();
    return mysqlClient
        .getWriterPool()
        .rxGetConnection()
        .flatMap(
            conn ->
                conn.rxBegin()
                    .flatMap(
                        tx ->
                            conn.preparedQuery(InsightJobQueries.DELETE_OLD_JOBS)
                                .rxExecute(
                                    Tuple.wrap(
                                        new Object[] {
                                          key.projectId(),
                                          key.insightType().name(),
                                          key.entityKey(),
                                          key.executionMode().name(),
                                          key.startDate() != null
                                              ? java.sql.Date.valueOf(key.startDate()) : null,
                                          key.endDate() != null
                                              ? java.sql.Date.valueOf(key.endDate()) : null,
                                          completed,
                                          jobId
                                        }))
                                .flatMap(
                                    deleteResult -> {
                                      log.debug(
                                          "Insight delete old completed rows: {}",
                                          deleteResult.rowCount());
                                      return conn.preparedQuery(InsightJobQueries.FINALIZE_SUCCESS)
                                          .rxExecute(Tuple.of(completed, jobId))
                                          .flatMap(
                                              finalizeResult -> {
                                                if (finalizeResult.rowCount() == 0) {
                                                  return Single.error(
                                                      new IllegalStateException(
                                                          "No insight job found to finalize: "
                                                              + jobId));
                                                }
                                                return tx.rxCommit()
                                                    .toSingleDefault(finalizeResult.rowCount());
                                              });
                                    })
                                .onErrorResumeNext(
                                    error ->
                                        tx.rxRollback()
                                            .onErrorComplete()
                                            .andThen(Single.error(error))))
                    .doFinally(conn::close))
        .ignoreElement()
        .doOnError(e -> log.warn("Insight job mark completed failed: {}", e.getMessage()));
  }

  public Completable markFailed(
      final String jobId,
      final InsightJobKey key,
      final String errorMessage) {
    final String failed = InsightJobStatus.FAILED.name();
    return mysqlClient
        .getWriterPool()
        .rxGetConnection()
        .flatMap(
            conn ->
                conn.rxBegin()
                    .flatMap(
                        tx ->
                            conn.preparedQuery(InsightJobQueries.DELETE_OLD_JOBS)
                                .rxExecute(
                                    Tuple.wrap(
                                        new Object[] {
                                          key.projectId(),
                                          key.insightType().name(),
                                          key.entityKey(),
                                          key.executionMode().name(),
                                          key.startDate() != null
                                              ? java.sql.Date.valueOf(key.startDate()) : null,
                                          key.endDate() != null
                                              ? java.sql.Date.valueOf(key.endDate()) : null,
                                          failed,
                                          jobId
                                        }))
                                .flatMap(
                                    deleteResult -> {
                                      log.debug(
                                          "Insight delete old failed rows: {}",
                                          deleteResult.rowCount());
                                      return conn.preparedQuery(InsightJobQueries.FINALIZE_FAILURE)
                                          .rxExecute(Tuple.of(failed, errorMessage, jobId))
                                          .flatMap(
                                              finalizeResult -> {
                                                if (finalizeResult.rowCount() == 0) {
                                                  return Single.error(
                                                      new IllegalStateException(
                                                          "No insight job found to finalize: "
                                                              + jobId));
                                                }
                                                return tx.rxCommit()
                                                    .toSingleDefault(finalizeResult.rowCount());
                                              });
                                    })
                                .onErrorResumeNext(
                                    error ->
                                        tx.rxRollback()
                                            .onErrorComplete()
                                            .andThen(Single.error(error))))
                    .doFinally(conn::close))
        .ignoreElement()
        .doOnError(
            e -> log.warn("Insight job mark failed status persist error: {}", e.getMessage()));
  }

  public Single<Integer> markStaleJobsFailed(final int thresholdMinutes) {
    final String pending = InsightJobStatus.PENDING.name();
    final String processing = InsightJobStatus.PROCESSING.name();
    final String failed = InsightJobStatus.FAILED.name();
    return mysqlClient
        .getWriterPool()
        .preparedQuery(InsightJobQueries.MARK_STALE_JOBS)
        .rxExecute(Tuple.of(failed, pending, processing, thresholdMinutes))
        .map(SqlResult::rowCount)
        .doOnError(e -> log.warn("Insight stale job cleanup failed: {}", e.getMessage()));
  }

  private static InsightJob mapRow(final Row row) {
    InsightType insightType = InsightType.valueOf(row.getString(2));
    InsightExecutionMode executionMode = InsightExecutionMode.valueOf(row.getString(4));
    InsightJobStatus status = InsightJobStatus.valueOf(row.getString(7));
    LocalDate startDate = row.getLocalDate(5);
    LocalDate endDate = row.getLocalDate(6);
    LocalDateTime createdLdt = row.getLocalDateTime(9);
    LocalDateTime startedLdt = row.getLocalDateTime(10);
    LocalDateTime completedLdt = row.getLocalDateTime(11);
    return InsightJob.builder()
        .jobId(row.getString(0))
        .projectId(row.getString(1))
        .insightType(insightType)
        .entityKey(row.getString(3))
        .executionMode(executionMode)
        .startDate(startDate)
        .endDate(endDate)
        .status(status)
        .errorMessage(row.getString(8))
        .createdAt(createdLdt != null ? createdLdt.toInstant(ZoneOffset.UTC) : null)
        .startedAt(startedLdt != null ? startedLdt.toInstant(ZoneOffset.UTC) : null)
        .completedAt(completedLdt != null ? completedLdt.toInstant(ZoneOffset.UTC) : null)
        .createdBy(row.getString(12))
        .build();
  }
}
