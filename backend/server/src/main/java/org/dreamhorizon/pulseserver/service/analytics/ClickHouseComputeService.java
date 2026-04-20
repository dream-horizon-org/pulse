package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;

/**
 * Executes ClickHouse INSERT queries for funnel and journey computation using per-project R2DBC
 * connection pools.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickHouseComputeService {

  private final FunnelDefinitionDao funnelDefinitionDao;
  private final JourneyDao journeyDao;
  private final ClickhouseProjectCredentialsDao clickhouseProjectCredentialsDao;
  private final ClickhouseProjectConnectionPoolManager poolManager;

  // ── On-save path ─────────────────────────────────────────────────────────────

  /**
   * Computes a single funnel by ID. Used on the on-save path (AUTO and ONCE modes).
   *
   * <p>Uses the funnel step-order-aware builder
   * ({@link ClickHouseFunnelComputeDao#buildInsertSqlForDefinition(FunnelDefinitionRow)}):
   * ORDERED funnels use the chain path (with medians); UNORDERED funnels use the sliding-window
   * distinct-step path (no medians). The legacy {@code windowFunnel}-based
   * {@link ClickHouseFunnelComputeDao#buildInsertSql} is retained as a backup.
   */
  public Single<Boolean> computeFunnel(Long funnelId) {
    return funnelDefinitionDao.findById(funnelId)
        .switchIfEmpty(Single.error(
            new IllegalArgumentException("Funnel not found: " + funnelId)))
        .flatMap(def -> executeInsert(def.getProjectId(),
            ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(def)));
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
   * <p>The legacy single-query batch builder
   * ({@link ClickHouseFunnelComputeDao#buildBatchInsertSql}) is retained as a backup.
   */
  public Single<Boolean> computeFunnelBatch(String projectId, List<FunnelDefinitionRow> defs) {
    if (defs == null || defs.isEmpty()) {
      return Single.just(true);
    }
    return Observable.fromIterable(defs)
        .concatMapSingle(def ->
            executeInsert(projectId, ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(def))
                .onErrorReturn(err -> {
                  log.error(
                      "Funnel compute failed for projectId={}, funnelId={}",
                      projectId, def.getId(), err);
                  return false;
                }))
        .toList()
        .map(results -> results.stream().allMatch(Boolean::booleanValue));
  }

  /**
   * Computes all provided journeys for a single project. Journeys with {@code direction == "START"}
   * are batched into one INSERT; remaining journeys (END) into another — same semantics as Spark.
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
      chain =
          chain.flatMap(
              __ ->
                  executeInsert(
                      projectId, ClickHouseJourneyComputeDao.buildBatchInsertSql(startDefs, "START")));
    }
    if (!endDefs.isEmpty()) {
      chain =
          chain.flatMap(
              __ ->
                  executeInsert(
                      projectId, ClickHouseJourneyComputeDao.buildBatchInsertSql(endDefs, "END")));
    }
    return chain;
  }

  /**
   * Matches Spark journey compute: only the literal {@code "START"} uses forward anchor semantics;
   * any other stored value uses END semantics.
   */
  private static String journeyDirectionForSql(JourneyRow row) {
    return "START".equals(row.getDirection()) ? "START" : "END";
  }

  // ── Core R2DBC helper ─────────────────────────────────────────────────────────

  /**
   * Executes a raw SQL INSERT against the per-project ClickHouse pool using a non-blocking R2DBC
   * chain. Returns {@code true} on success.
   */
  Single<Boolean> executeInsert(String projectId, String sql) {
    if (sql == null || sql.isBlank()) {
      return Single.just(true);
    }
    return clickhouseProjectCredentialsDao
        .getCredentialsByProjectId(projectId)
        .switchIfEmpty(
            Maybe.error(
                new IllegalStateException(
                    "No ClickHouse credentials configured for project: " + projectId)))
        .toSingle()
        .flatMap(
            creds -> {
              var pool =
                  poolManager.getPoolForProject(
                      projectId,
                      creds.getClickhouseUsername(),
                      creds.getClickhousePasswordEncrypted());

              return Single.fromPublisher(pool.create())
                  .flatMap(
                      conn ->
                          Flowable.fromPublisher(conn.createStatement(sql).execute())
                              .flatMap(result -> Flowable.fromPublisher(result.getRowsUpdated()))
                              .reduce(0L, Long::sum)
                              .map(rows -> true)
                              .doFinally(() -> Completable.fromPublisher(conn.close()).subscribe()))
                  .doOnError(
                      err ->
                          log.error(
                              "ClickHouse INSERT failed for project {}: {}",
                              projectId,
                              err.getMessage()));
            });
  }
}
