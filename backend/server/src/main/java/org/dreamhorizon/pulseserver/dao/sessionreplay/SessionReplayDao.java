package org.dreamhorizon.pulseserver.dao.sessionreplay;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.sessionreplay.query.SessionReplayQueries;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.session.models.BlockListing;
import org.dreamhorizon.pulseserver.dao.sessionreplay.models.BlockListingQueryRow;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionReplayDao {
  private final ClickhouseQueryService clickhouseQueryService;

  private static final int DEFAULT_TIMEOUT_MS = 5000;
  private static final String SESSION_ID_PLACEHOLDER = "session_id";


  public Single<BlockListing> queryBlockListing(String sessionId) {
    String projectId = ProjectContext.requireProjectId();

    Map<String, Object> substitutionMap = new HashMap<>();
    substitutionMap.put(SESSION_ID_PLACEHOLDER, sessionId);

    String formattedQuery = new StringSubstitutor(substitutionMap)
        .replace(SessionReplayQueries.GET_BLOCK_LISTING_QUERY);

    QueryConfiguration config = QueryConfiguration
        .newQuery(formattedQuery)
        .timeoutMs(DEFAULT_TIMEOUT_MS)
        .projectId(projectId)
        .build();

    return clickhouseQueryService.executeQueryOrCreateJob(config, BlockListingQueryRow.class)
        .map(result -> {
          if (result.getRows() == null || result.getRows().isEmpty()) {
            return BlockListing.builder()
                .blockFirstTimestamps(List.of())
                .blockLastTimestamps(List.of())
                .blockUrls(List.of())
                .snapshotSource(null)
                .build();
          }
          BlockListingQueryRow row = result.getRows().get(0);
          return BlockListing.builder()
              .blockFirstTimestamps(parseStringArray(row.getBlockFirstTimestamps()))
              .blockLastTimestamps(parseStringArray(row.getBlockLastTimestamps()))
              .blockUrls(parseStringArray(row.getBlockUrls()))
              .snapshotSource(row.getSnapshotSource())
              .build();
        });
  }

  private List<String> parseStringArray(String arrayStr) {
    if (arrayStr == null || arrayStr.isEmpty() || "[]".equals(arrayStr)) {
      return List.of();
    }
    String inner = arrayStr;
    if (inner.startsWith("[")) {
      inner = inner.substring(1);
    }
    if (inner.endsWith("]")) {
      inner = inner.substring(0, inner.length() - 1);
    }
    List<String> result = new ArrayList<>();
    for (String element : inner.split(",")) {
      String trimmed = element.trim();
      if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
        trimmed = trimmed.substring(1, trimmed.length() - 1);
      }
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }
}
