package org.dreamhorizon.pulseserver.dao.rcajob;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
      final String interactionName,
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
                    interactionName,
                    date,
                    RcaJobStatus.PENDING.name(),
                    null,
                    createdBy,
                    null,
                    1)))
        .ignoreElement()
        .andThen(
            Single.fromCallable(
                () ->
                    new RcaReportJob(
                        jobId,
                        projectId,
                        interactionName,
                        date,
                        RcaJobStatus.PENDING,
                        null,
                        Instant.now(),
                        null,
                        null,
                        createdBy,
                        null,
                        1)))
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
      String projectId, String interactionName, LocalDate date) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(RcaReportJobQueries.GET_ACTIVE_JOB_BY_KEY)
        .rxExecute(Tuple.of(projectId, interactionName, date))
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
    String name = status.name();
    return mysqlClient
        .getWriterPool()
        .preparedQuery(RcaReportJobQueries.UPDATE_STATUS)
        .rxExecute(Tuple.of(name, name, jobId))
        .ignoreElement()
        .doOnError(e -> log.warn("RCA report job update status failed: {}", e.getMessage()));
  }

  public Completable markCompleted(
      String jobId,
      String projectId,
      String interactionName,
      LocalDate date) {
    Completable deleteOld =
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.DELETE_OLD_COMPLETED)
            .rxExecute(Tuple.of(projectId, interactionName, date, jobId))
            .ignoreElement()
            .doOnError(e -> log.warn("RCA delete old completed row failed: {}", e.getMessage()))
            .onErrorComplete();
    return deleteOld.andThen(
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.UPDATE_COMPLETED)
            .rxExecute(Tuple.of(jobId))
            .ignoreElement()
            .doOnError(e -> log.warn("RCA report job mark completed failed: {}", e.getMessage())));
  }

  public Completable markFailed(
      String jobId,
      String projectId,
      String interactionName,
      LocalDate date,
      String errorMessage) {
    Completable deleteOld =
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.DELETE_OLD_FAILED)
            .rxExecute(Tuple.of(projectId, interactionName, date, jobId))
            .ignoreElement()
            .doOnError(e -> log.warn("RCA delete old failed row failed: {}", e.getMessage()))
            .onErrorComplete();
    return deleteOld.andThen(
        mysqlClient
            .getWriterPool()
            .preparedQuery(RcaReportJobQueries.UPDATE_FAILED)
            .rxExecute(Tuple.of(errorMessage, jobId))
            .ignoreElement()
            .doOnError(
                e -> log.warn("RCA report job persist FAILED status failed: {}", e.getMessage())));
  }

  public Single<List<String>> listStaleJobIds() {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(RcaReportJobQueries.LIST_STALE_JOBS)
        .rxExecute(Tuple.tuple())
        .map(
            rows -> {
              List<String> ids = new ArrayList<>();
              for (Row row : rows) {
                ids.add(row.getString(0));
              }
              return ids;
            })
        .doOnError(e -> log.warn("RCA report job list stale failed: {}", e.getMessage()));
  }

  private static RcaReportJob mapRow(Row row) {
    String statusStr = row.getString(4);
    RcaJobStatus status = RcaJobStatus.valueOf(statusStr);
    LocalDateTime created = row.getLocalDateTime(6);
    LocalDateTime started = row.getLocalDateTime(7);
    LocalDateTime completed = row.getLocalDateTime(8);
    return new RcaReportJob(
        row.getString(0),
        row.getString(1),
        row.getString(2),
        row.getLocalDate(3),
        status,
        row.getString(5),
        created != null ? created.toInstant(ZoneOffset.UTC) : null,
        started != null ? started.toInstant(ZoneOffset.UTC) : null,
        completed != null ? completed.toInstant(ZoneOffset.UTC) : null,
        row.getString(9),
        row.getString(10),
        row.getInteger(11));
  }
}
