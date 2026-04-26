package org.dreamhorizon.pulseserver.dao.cronjobhistory;

import static org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryQueries.FAIL_STALE_IN_PROGRESS;
import static org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryQueries.INSERT_IF_NO_ACTIVE_IN_PROGRESS;
import static org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryQueries.MARK_COMPLETED;
import static org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryQueries.MARK_FAILED;
import static org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryQueries.SELECT_ACTIVE_IN_PROGRESS_ID;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CronJobHistoryDao {

  private final MysqlClient mysqlClient;

  /**
   * Marks stale {@code IN_PROGRESS} rows as {@code FAILED}, then inserts a new row if no fresh
   * in-progress job exists for {@code jobType}; otherwise returns the active job id (deduplicated).
   */
  public Single<CronJobEnqueueResult> enqueueOrDeduplicate(
      String jobType,
      Instant staleBeforeExclusive,
      String staleReclaimedMessage) {
    LocalDateTime staleBeforeLocal = LocalDateTime.ofInstant(staleBeforeExclusive, ZoneOffset.UTC);

    return mysqlClient.getWriterPool().rxGetConnection().flatMap(conn ->
        conn.begin()
            .flatMap(tx ->
                conn.preparedQuery(FAIL_STALE_IN_PROGRESS)
                    .rxExecute(Tuple.of(staleReclaimedMessage, jobType, staleBeforeLocal))
                    .doOnSuccess(rows -> {
                      if (rows.rowCount() > 0) {
                        log.warn(
                            "Marked {} stale IN_PROGRESS cron_jobs_history rows as FAILED for job_type={}",
                            rows.rowCount(),
                            jobType);
                      }
                    })
                    .flatMap(rows -> conn.preparedQuery(INSERT_IF_NO_ACTIVE_IN_PROGRESS)
                        .rxExecute(Tuple.of(jobType, jobType, staleBeforeLocal)))
                    .flatMap(insertResult -> {
                      if (insertResult.rowCount() > 0) {
                        Object lid = insertResult.property(MySQLClient.LAST_INSERTED_ID);
                        long jobId = Long.parseLong(String.valueOf(lid));
                        return Single.just(new CronJobEnqueueResult(jobId, false));
                      }
                      return conn.preparedQuery(SELECT_ACTIVE_IN_PROGRESS_ID)
                          .rxExecute(Tuple.of(jobType, staleBeforeLocal))
                          .flatMap(selectRows -> {
                            var it = selectRows.iterator();
                            if (!it.hasNext()) {
                              return Single.error(new IllegalStateException(
                                  "Cron enqueue race: no insert and no active IN_PROGRESS for job_type="
                                      + jobType));
                            }
                            Row row = it.next();
                            return Single.just(new CronJobEnqueueResult(row.getLong("id"), true));
                          });
                    })
                    .flatMap(result -> tx.rxCommit().toSingleDefault(result))
                    .onErrorResumeNext(err -> tx.rxRollback()
                        .doOnError(rollbackErr ->
                            log.warn("cron_jobs_history transaction rollback failed", rollbackErr))
                        .onErrorComplete()
                        .andThen(Single.error(err)))
            )
            .doFinally(conn::close));
  }

  public Completable markCompleted(long jobId) {
    return mysqlClient.getWriterPool()
        .preparedQuery(MARK_COMPLETED)
        .rxExecute(Tuple.of(jobId))
        .ignoreElement();
  }

  public Completable markFailed(long jobId, String errorMessage) {
    String safeMessage = truncateError(errorMessage);
    return mysqlClient.getWriterPool()
        .preparedQuery(MARK_FAILED)
        .rxExecute(Tuple.of(safeMessage, jobId))
        .ignoreElement();
  }

  private static String truncateError(String message) {
    if (message == null) {
      return null;
    }
    int max = 8000;
    return message.length() <= max ? message : message.substring(0, max);
  }
}
