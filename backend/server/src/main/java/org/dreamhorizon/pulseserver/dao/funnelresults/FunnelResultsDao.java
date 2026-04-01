package org.dreamhorizon.pulseserver.dao.funnelresults;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelResultsDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /** Rows for the latest {@code run_time} for this funnel, ordered by step. */
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

  private static List<FunnelResultRow> rowsOrEmpty(QueryResultResponse<FunnelResultRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }
}
