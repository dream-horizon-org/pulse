package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseWriteClient;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickHouseComputeService {

  private final FunnelDefinitionDao funnelDefinitionDao;
  private final JourneyDao journeyDao;
  private final AnalyticsJobDao analyticsJobDao;
  private final ClickhouseWriteClient clickhouseWriteClient;

  // ── On-save path ─────────────────────────────────────────────────────────────

  /**
   * Computes a single funnel by ID. Used on the on-save path (AUTO and ONCE modes).
   *
   * <p>Uses the funnel step-order-aware builder
   * ({@link ClickHouseFunnelComputeDao#buildInsertSqlForDefinition(FunnelDefinitionRow)}):
   * ORDERED funnels use the chain path (with medians); UNORDERED funnels use the sliding-window
   * distinct-step path (no medians). The legacy {@code windowFunnel}-based
   * {@link ClickHouseFunnelComputeDao#buildInsertSql} is retained as a backup.
   *
   * <p>Also emits drop-off bridge rows ({@code funnel_session_state} + {@code funnel_user_state})
   * for ORDERED funnels, stamped with the same {@code RunTime} as {@code funnel_results} so the
   * drop-off DAO can align them by {@code MAX(RunTime)}.
   */
  public Single<Boolean> computeFunnel(Long funnelId) {
    return funnelDefinitionDao.findById(funnelId)
      .switchIfEmpty(Single.error(
        new IllegalArgumentException("Funnel not found: " + funnelId)))
      .flatMap(def -> computeOne(def));
  }

  /**
   * Runs the three INSERTs for one funnel with a shared {@code RunTime}:
   * {@code funnel_results} → {@code funnel_session_state} → {@code funnel_user_state}.
   * Bridge inserts are best-effort (they never fail the main compute) — if they error, the
   * primary funnel result still lands but the drop-off side-panel will show zero causes for
   * this run.
   */
  private Single<Boolean> computeOne(FunnelDefinitionRow def) {
    String runTime = ClickHouseFunnelComputeDao.newRunTimeLiteral();
    String projectId = def.getProjectId();

    return executeInsert(projectId, ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(def, runTime))
      .flatMap(resultsOk -> emitDropoffBridge(def, runTime).map(ignored -> resultsOk));
  }

  /**
   * Fires the bridge inserts. What fires depends on funnel mode:
   * <ul>
   *   <li>SESSIONS funnels → only {@code funnel_session_state} (per-session windowFunnel chain).
   *       Drop-off DAO uses this for cohort + correlation.</li>
   *   <li>UNIQUE_USERS funnels → BOTH {@code funnel_session_state} AND
   *       {@code funnel_user_state}. The user_state table (cross-session windowFunnel chain
   *       directly on {@code otel_logs}) drives the cohort + lift numbers shown in the panel.
   *       The session_state table is written purely to power the x-ray drill-in
   *       ("show all of this user's attempts") and the single-session debug view — its rows
   *       don't feed the cohort calculation in UNIQUE_USERS mode.</li>
   * </ul>
   * The two tables are independent (user_state no longer derives from session_state), so order
   * doesn't matter. Both inserts are issued; either may no-op for unordered/zero-step funnels.
   *
   * <p>Never fails the upstream funnel compute — errors are logged and swallowed.
   */
  private Single<Boolean> emitDropoffBridge(FunnelDefinitionRow def, String runTime) {
    String projectId = def.getProjectId();
    String sessionSql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(def, runTime);
    String userSql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(def, runTime);

    return executeInsert(projectId, sessionSql)
      .flatMap(ok -> executeInsert(projectId, userSql))
      .onErrorReturn(err -> {
        log.warn(
          "Drop-off bridge emission failed for projectId={}, funnelId={}: {}",
          projectId, def.getId(), err.getMessage());
        return true;
      });
  }

  /**
   * Computes a single journey by ID. Used on the on-save path (AUTO and ONCE modes).
   * Writes one direction only, matching Spark: {@code journey.direction == "START"} → START semantics;
   * otherwise END semantics.
   */
  public Single<Boolean> computeJourney(Long journeyId) {
    return journeyDao.findById(journeyId)
      .switchIfEmpty(Single.error(
        new IllegalArgumentException("Journey not found: " + journeyId)))
      .flatMap(def ->
        executeInsert(
          def.getProjectId(),
          ClickHouseJourneyComputeDao.buildInsertSql(def, journeyDirectionForSql(def))));
  }

  // ── Batch path ────────────────────────────────────────────────────────────────

  /**
   * Computes all provided funnels for a single project.
   *
   * <p>Runs one chain-based INSERT per funnel sequentially. Sequential (not parallel) within
   * a project to preserve the ClickHouse load characteristic of the prior shared-scan batch
   * (one concurrent query per project). Per-funnel failure is isolated via
   * {@code onErrorReturn}; the project batch succeeds only if all funnels succeed.
   *
   * <p>For each funnel we also write a per-item {@code analytics_jobs} row with
   * {@code job_type = 'FUNNEL'} and {@code reference_id = funnel.id} so the listing's
   * {@code latest_job_status} subquery reflects the latest cron run (not just the last
   * on-save). On success we also bump {@code funnel.updated_at} so the "Last updated" column
   * advances on each scheduled run.
   *
   * <p>The legacy single-query batch builder
   * ({@link ClickHouseFunnelComputeDao#buildBatchInsertSql}) is retained as a backup.
   */
  public Single<Boolean> computeFunnelBatch(String projectId, List<FunnelDefinitionRow> defs) {
    if (defs == null || defs.isEmpty()) {
      return Single.just(true);
    }
    return Observable.fromIterable(defs)
      .concatMapSingle(def -> computeAndRecordFunnel(projectId, def))
      .toList()
      .map(results -> results.stream().allMatch(Boolean::booleanValue));
  }

  /**
   * Computes a single funnel inside a batch run, recording a per-item {@code analytics_jobs}
   * row (RUNNING → SUCCEEDED/FAILED) tied to the funnel id, and bumping {@code updated_at}
   * on success. The compute boolean propagates upstream; per-item bookkeeping failures only log.
   */
  private Single<Boolean> computeAndRecordFunnel(String projectId, FunnelDefinitionRow def) {
    final long funnelId = def.getId();
    final LocalDateTime startedAt = LocalDateTime.now();
    return analyticsJobDao
      .insertJob(AnalyticsJobType.FUNNEL, funnelId, null, AnalyticsJobStatus.RUNNING)
      .onErrorResumeNext(insertErr -> {
        log.warn("Failed to insert RUNNING analytics_jobs row for funnelId={}: {}",
          funnelId, insertErr.getMessage());
        return Single.just(-1L);
      })
      .flatMap(jobDbId ->
        executeInsert(projectId, ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(def))
          .flatMap(success -> finalizeFunnelJob(funnelId, jobDbId, success, null, startedAt))
          .onErrorResumeNext(err -> {
            log.error("Funnel compute failed for projectId={}, funnelId={}",
              projectId, funnelId, err);
            return finalizeFunnelJob(funnelId, jobDbId, false, err.getMessage(), startedAt);
          }));
  }

  private Single<Boolean> finalizeFunnelJob(
    long funnelId, long jobDbId, boolean success, String errorMessage, LocalDateTime startedAt) {
    LocalDateTime completedAt = LocalDateTime.now();
    AnalyticsJobStatus status = success ? AnalyticsJobStatus.SUCCEEDED : AnalyticsJobStatus.FAILED;

    Single<Integer> updateJob = jobDbId > 0
      ? analyticsJobDao.updateJobStatus(jobDbId, status, errorMessage, startedAt, completedAt)
      .onErrorReturn(err -> {
        log.warn("Failed to update analytics_jobs row id={} for funnelId={}: {}",
          jobDbId, funnelId, err.getMessage());
        return 0;
      })
      : Single.just(0);

    return updateJob.map(__ -> success);
  }

  /**
   * Computes all provided journeys for a single project. Journeys with {@code direction == "START"}
   * are batched into one INSERT; remaining journeys (END) into another — same semantics as Spark.
   *
   * <p>For each journey we also write a per-item {@code analytics_jobs} row with
   * {@code job_type = 'JOURNEY'} and {@code reference_id = journey.id} so the listing's
   * {@code latest_job_status} subquery reflects the latest cron run. Because journeys in a
   * direction batch share a single ClickHouse INSERT, all rows share the same
   * SUCCEEDED/FAILED outcome.
   */
  public Single<Boolean> computeJourneyBatch(String projectId, List<JourneyRow> defs) {
    if (defs == null || defs.isEmpty()) {
      return Single.just(true);
    }
    List<JourneyRow> startDefs =
      defs.stream().filter(d -> "START".equals(d.getDirection())).toList();
    List<JourneyRow> endDefs =
      defs.stream().filter(d -> !"START".equals(d.getDirection())).toList();

    Single<Boolean> chain = Single.just(true);
    if (!startDefs.isEmpty()) {
      chain = chain.flatMap(__ -> computeAndRecordJourneyDirection(projectId, startDefs, "START"));
    }
    if (!endDefs.isEmpty()) {
      chain = chain.flatMap(prev ->
        computeAndRecordJourneyDirection(projectId, endDefs, "END")
          .map(curr -> prev && curr));
    }
    return chain;
  }

  /**
   * Runs one direction's batch INSERT and writes per-journey {@code analytics_jobs} rows.
   * RUNNING rows are inserted upfront so a long-running ClickHouse INSERT is observable in MySQL;
   * after the INSERT resolves, every row is updated to SUCCEEDED or FAILED en bloc.
   */
  private Single<Boolean> computeAndRecordJourneyDirection(
    String projectId, List<JourneyRow> directionDefs, String direction) {
    final LocalDateTime startedAt = LocalDateTime.now();
    return Observable.fromIterable(directionDefs)
      .flatMapSingle(def ->
        analyticsJobDao
          .insertJob(AnalyticsJobType.JOURNEY, def.getId(), null, AnalyticsJobStatus.RUNNING)
          .onErrorReturn(err -> {
            log.warn("Failed to insert RUNNING analytics_jobs row for journeyId={}: {}",
              def.getId(), err.getMessage());
            return -1L;
          })
          .map(jobDbId -> new JourneyJobHandle(def.getId(), jobDbId)))
      .toList()
      .flatMap(handles ->
        executeInsert(projectId, ClickHouseJourneyComputeDao.buildBatchInsertSql(directionDefs, direction))
          .flatMap(success -> finalizeJourneyHandles(handles, success, null, startedAt))
          .onErrorResumeNext(err -> {
            log.error("Journey {} batch compute failed for projectId={}, count={}",
              direction, projectId, directionDefs.size(), err);
            return finalizeJourneyHandles(handles, false, err.getMessage(), startedAt);
          }));
  }

  private Single<Boolean> finalizeJourneyHandles(
    List<JourneyJobHandle> handles, boolean success, String errorMessage, LocalDateTime startedAt) {
    LocalDateTime completedAt = LocalDateTime.now();
    AnalyticsJobStatus status = success ? AnalyticsJobStatus.SUCCEEDED : AnalyticsJobStatus.FAILED;

    return Observable.fromIterable(handles)
      .flatMapSingle(h -> {
        Single<Integer> updateJob = h.jobDbId() > 0
          ? analyticsJobDao.updateJobStatus(h.jobDbId(), status, errorMessage, startedAt, completedAt)
          .onErrorReturn(err -> {
            log.warn("Failed to update analytics_jobs row id={} for journeyId={}: {}",
              h.jobDbId(), h.journeyId(), err.getMessage());
            return 0;
          })
          : Single.just(0);

        return updateJob.map(__ -> true);
      })
      .toList()
      .map(__ -> success)
      .subscribeOn(Schedulers.io());
  }

  /**
   * Pairs a journey id with the analytics_jobs row id created for its current run.
   */
  private record JourneyJobHandle(long journeyId, long jobDbId) {
  }

  // ── Cascading-delete helpers ─────────────────────────────────────────────────

  /**
   * Best-effort delete of every row this funnel produced across the four ClickHouse tables it
   * touches: {@code funnel_results} plus the three drop-off correlation tables
   * ({@code funnel_session_state}, {@code funnel_user_state}, {@code funnel_dropoff_attribution}).
   * Used by {@code FunnelService.delete} so a removed funnel doesn't leave orphan rows that the
   * drop-off panel would still surface (TTL is 90 days — too long to wait).
   *
   * <p>ClickHouse {@code DELETE} is asynchronous; this kicks off four mutations and returns
   * success once all four statements are accepted by the server. The bridge-table deletes are
   * best-effort: a failure on any of them is logged but doesn't abort the cascade — the
   * primary {@code funnel_results} delete is what {@code FunnelService.delete} actually depends on.
   */
  public Single<Boolean> deleteFunnelResults(String projectId, long funnelId) {
    String safeProject = projectId == null ? "" : projectId.replace("'", "''");
    String where = "WHERE ProjectId = '" + safeProject + "' AND FunnelId = " + funnelId;

    Single<Boolean> primary = executeInsert(projectId, "DELETE FROM otel.funnel_results " + where);

    Single<Boolean> bridges = executeInsert(projectId, "DELETE FROM otel.funnel_session_state " + where)
      .flatMap(ok -> executeInsert(projectId, "DELETE FROM otel.funnel_user_state " + where))
      .flatMap(ok -> executeInsert(projectId, "DELETE FROM otel.funnel_dropoff_attribution " + where))
      .onErrorReturn(err -> {
        log.warn(
          "Drop-off cascade delete failed for projectId={}, funnelId={}: {}",
          safeProject, funnelId, err.getMessage());
        return true;
      });

    return primary.flatMap(primaryOk -> bridges.map(bridgesOk -> primaryOk));
  }

  /**
   * Best-effort delete of every {@code otel.journey_results} row for a single journey.
   * Used by {@code JourneyService.delete}.
   */
  public Single<Boolean> deleteJourneyResults(String projectId, long journeyId) {
    String safeProject = projectId == null ? "" : projectId.replace("'", "''");
    String sql =
      "DELETE FROM otel.journey_results "
        + "WHERE ProjectId = '" + safeProject + "' AND JourneyId = " + journeyId;
    return executeInsert(projectId, sql);
  }

  /**
   * Matches Spark journey compute: only the literal {@code "START"} uses forward anchor semantics;
   * any other stored value uses END semantics.
   */
  private static String journeyDirectionForSql(JourneyRow row) {
    return "START".equals(row.getDirection()) ? "START" : "END";
  }

  Single<Boolean> executeInsert(String projectId, String sql) {
    if (sql == null || sql.isBlank()) {
      return Single.just(true);
    }
    return clickhouseWriteClient
      .executeSql(sql)
      .doOnError(
        err ->
          log.error(
            "ClickHouse INSERT failed for project {}: {}",
            projectId,
            err.getMessage()));
  }
}
