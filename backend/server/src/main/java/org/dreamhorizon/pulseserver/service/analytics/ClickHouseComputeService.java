package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
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
   */
  public Single<Boolean> computeFunnel(Long funnelId) {
    return funnelDefinitionDao.findById(funnelId)
        .switchIfEmpty(Single.error(
            new IllegalArgumentException("Funnel not found: " + funnelId)))
        .flatMap(def -> executeInsert(def.getProjectId(),
            ClickHouseFunnelComputeDao.buildInsertSql(def)));
  }

  /**
   * Computes a single journey by ID. Used on the on-save path (AUTO and ONCE modes).
   * Runs START then END sequentially.
   */
  public Single<Boolean> computeJourney(Long journeyId) {
    return journeyDao.findById(journeyId)
        .switchIfEmpty(Single.error(
            new IllegalArgumentException("Journey not found: " + journeyId)))
        .flatMap(def ->
            executeInsert(def.getProjectId(), ClickHouseJourneyComputeDao.buildInsertSql(def, "START"))
                .flatMap(__ -> executeInsert(def.getProjectId(),
                    ClickHouseJourneyComputeDao.buildInsertSql(def, "END"))));
  }

  // ── Batch path ────────────────────────────────────────────────────────────────

  /**
   * Computes all provided funnels for a single project in one query.
   */
  public Single<Boolean> computeFunnelBatch(String projectId, List<FunnelDefinitionRow> defs) {
    return executeInsert(projectId, ClickHouseFunnelComputeDao.buildBatchInsertSql(defs));
  }

  /**
   * Computes all provided journeys for a single project in one query (START then END).
   */
  public Single<Boolean> computeJourneyBatch(String projectId, List<JourneyRow> defs) {
    return executeInsert(projectId, ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START"))
        .flatMap(__ -> executeInsert(projectId,
            ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "END")));
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
        .switchIfEmpty(Single.error(
            new IllegalStateException("No ClickHouse credentials found for project: " + projectId)))
        .flatMap(creds -> {
          var pool = poolManager.getPoolForProject(
              projectId,
              creds.getClickhouseUsername(),
              creds.getClickhousePasswordEncrypted());

          return Single.fromPublisher(pool.create())
              .flatMap(conn ->
                  Flowable.fromPublisher(conn.createStatement(sql).execute())
                      .flatMap(result -> Flowable.fromPublisher(result.getRowsUpdated()))
                      .reduce(0L, Long::sum)
                      .map(rows -> true)
                      .doFinally(() -> Completable.fromPublisher(conn.close()).subscribe()))
              .doOnError(err ->
                  log.error("ClickHouse INSERT failed for project {}: {}", projectId, err.getMessage()));
        });
  }
}
