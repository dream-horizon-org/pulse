package org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;

import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class JourneyResultsDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Rows for the latest {@code RunTime} for this journey and direction.
   */
  public Single<List<JourneyResultRow>> queryLatest(
    String projectId, long journeyId, String direction) {
    String sql = JourneyResultsQueries.buildLatestResultsSql(projectId, journeyId, direction);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, JourneyResultRow.class)
      .map(JourneyResultsDao::rowsOrEmpty);
  }

  private static List<JourneyResultRow> rowsOrEmpty(QueryResultResponse<JourneyResultRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }
}
