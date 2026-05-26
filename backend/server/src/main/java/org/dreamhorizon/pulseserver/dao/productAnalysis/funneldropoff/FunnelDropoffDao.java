package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffEvidenceRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

/**
 * Data access for funnel drop-off attribution reads and evidence drill-in.
 *
 * <p>{@link #queryCauses} tries precomputed {@code funnel_dropoff_attribution} first, then
 * falls back to live OTel joins when empty. {@link #queryCausesFromAttribution} is
 * attribution-only (used by funnel RCA).
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelDropoffDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Ranked drop-off causes for the given step of the given funnel run.
   *
   * <p>Two-tier read: precomputed attribution first; live join when empty so older runs
   * and freshly-created funnels still populate the panel.
   *
   * @param runTime optional; {@code null} picks the latest run for the funnel.
   * @param mode    either {@code UNIQUE_USERS} or {@code SESSIONS} (case-insensitive).
   */
  public Single<List<FunnelDropoffCauseRow>> queryCauses(
      String projectId, long funnelId, int stepIndex, String runTime, String mode) {
    return queryCausesFromAttribution(projectId, funnelId, stepIndex, runTime)
        .flatMap(rows -> rows.isEmpty()
            ? queryCausesLive(projectId, funnelId, stepIndex, runTime, mode)
            : Single.just(rows));
  }

  /**
   * Attribution-only read used by drop-off panel (after precompute) and funnel RCA.
   */
  public Single<List<FunnelDropoffCauseRow>> queryCausesFromAttribution(
      String projectId, long funnelId, int stepIndex, String runTime) {
    String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
        projectId, funnelId, stepIndex, runTime);
    return executeCauseQuery(projectId, sql);
  }

  private Single<List<FunnelDropoffCauseRow>> queryCausesLive(
      String projectId, long funnelId, int stepIndex, String runTime, String mode) {
    String sql = FunnelDropoffQueries.buildCausesSql(projectId, funnelId, stepIndex, runTime, mode);
    return executeCauseQuery(projectId, sql);
  }

  private Single<List<FunnelDropoffCauseRow>> executeCauseQuery(String projectId, String sql) {
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, FunnelDropoffCauseRow.class)
        .map(FunnelDropoffDao::causeRowsOrEmpty);
  }

  /**
   * Evidence rows for the side-panel's "View examples" drill-in. {@code sessionIds}
   * is the subset picked from {@link FunnelDropoffCauseRow#getExampleSessions()} —
   * typically 5 per cause.
   */
  public Single<List<FunnelDropoffEvidenceRow>> queryEvidence(
      String projectId,
      long funnelId,
      int stepIndex,
      String runTime,
      String mode,
      List<String> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) {
      return Single.just(Collections.emptyList());
    }
    String sql = FunnelDropoffQueries.buildEvidenceSql(
        projectId, funnelId, stepIndex, runTime, mode, sessionIds);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, FunnelDropoffEvidenceRow.class)
        .map(FunnelDropoffDao::evidenceRowsOrEmpty);
  }

  private static List<FunnelDropoffCauseRow> causeRowsOrEmpty(
      QueryResultResponse<FunnelDropoffCauseRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }

  private static List<FunnelDropoffEvidenceRow> evidenceRowsOrEmpty(
      QueryResultResponse<FunnelDropoffEvidenceRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }
}
