package org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelConversionSummaryRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelResultsDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Rows for the latest {@code RunTime} for this funnel, ordered by step.
   */
  public Single<List<FunnelResultRow>> queryLatest(String projectId, long funnelId) {
    String sql = FunnelResultsQueries.buildLatestResultsSql(projectId, funnelId);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, FunnelResultRow.class)
      .map(FunnelResultsDao::rowsOrEmpty);
  }

  /**
   * Overall conversion rate and trend for a batch of funnels. Returns a map of
   * funnelId → summary (rate + trend). Funnels without results are absent from the map.
   */
  public Single<Map<Long, FunnelConversionSummaryRow>> queryConversionSummaries(
      String projectId, List<Long> funnelIds) {
    if (funnelIds == null || funnelIds.isEmpty()) {
      return Single.just(Map.of());
    }
    String sql = FunnelResultsQueries.buildBulkOverallConversionRates(projectId, funnelIds);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, FunnelConversionSummaryRow.class)
      .map(response -> {
        Map<Long, FunnelConversionSummaryRow> map = new HashMap<>();
        if (response != null && response.getRows() != null) {
          for (FunnelConversionSummaryRow row : response.getRows()) {
            if (row.getFunnelId() != null) {
              map.put(row.getFunnelId(), row);
            }
          }
        }
        return map;
      });
  }

  private static List<FunnelResultRow> rowsOrEmpty(QueryResultResponse<FunnelResultRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }
}
