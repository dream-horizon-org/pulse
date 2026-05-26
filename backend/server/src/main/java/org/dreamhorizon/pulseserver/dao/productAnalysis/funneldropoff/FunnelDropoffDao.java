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
 * Data access for funnel drop-off attribution reads.
 *
 * <p>Cause reads use only {@code otel.funnel_dropoff_attribution} (precomputed at funnel compute).
 * There is no live OTel join fallback.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelDropoffDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Ranked drop-off causes from precomputed attribution. {@code mode} is accepted for API
   * compatibility but does not change the query.
   */
  public Single<List<FunnelDropoffCauseRow>> queryCauses(
      String projectId, long funnelId, int stepIndex, String runTime, String mode) {
    return queryCausesFromAttribution(projectId, funnelId, stepIndex, runTime);
  }

  /** Attribution-only read used by drop-off panel and funnel RCA. */
  public Single<List<FunnelDropoffCauseRow>> queryCausesFromAttribution(
      String projectId, long funnelId, int stepIndex, String runTime) {
    String sql =
        FunnelDropoffQueries.buildCausesSqlFromAttribution(projectId, funnelId, stepIndex, runTime);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    // Server-built SQL already filters ProjectId; global pool avoids per-project CH credentials
    // (needed for local dev when fancode has MySQL funnel rows but no clickhouse_project_credentials).
    return clickhouseQueryService
        .executeGenericQueryWithGlobalPool(config, FunnelDropoffCauseRow.class)
        .map(FunnelDropoffDao::causeRowsOrEmpty);
  }

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
    String sql =
        FunnelDropoffQueries.buildEvidenceSql(
            projectId, funnelId, stepIndex, runTime, mode, sessionIds);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeGenericQueryWithGlobalPool(config, FunnelDropoffEvidenceRow.class)
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
