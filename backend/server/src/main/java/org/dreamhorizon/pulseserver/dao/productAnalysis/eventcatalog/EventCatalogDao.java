package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogEventNameRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogFilterKeyRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EventCatalogDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Event names for {@code FilterKey = 'EVENT'}, ordered lexicographically.
   */
  public Single<List<String>> listEventNames(String projectId) {
    String sql = EventCatalogQueries.buildListEventNamesSql(projectId);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, EventCatalogEventNameRow.class)
      .map(EventCatalogDao::toNames);
  }

  /**
   * Distinct {@code ScreenName} from {@code otel.otel_traces} where {@code PulseType = screen_load}.
   */
  public Single<List<String>> listScreenNames(String projectId) {
    String sql = EventCatalogQueries.buildListScreenNamesSql(projectId);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, EventCatalogEventNameRow.class)
      .map(EventCatalogDao::toNames);
  }

  /**
   * Distinct {@code FilterKey} values except {@code EVENT}, ordered lexicographically.
   */
  public Single<List<String>> listFilterKeys(String projectId) {
    String sql = EventCatalogQueries.buildListFilterKeysSql(projectId);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, EventCatalogFilterKeyRow.class)
      .map(EventCatalogDao::toFilterKeys);
  }

  /**
   * Distinct {@code FilterValue} for the given {@code FilterKey}, ordered lexicographically.
   */
  public Single<List<String>> listFilterValues(String projectId, String filterKey) {
    String sql = EventCatalogQueries.buildListFilterValuesSql(projectId, filterKey);
    QueryConfiguration config =
      QueryConfiguration.newQuery(sql)
        .timeoutMs(TIMEOUT_MS)
        .tenantId(projectId)
        .projectId(projectId)
        .build();
    return clickhouseQueryService
      .executeQueryOrCreateJob(config, EventCatalogEventNameRow.class)
      .map(EventCatalogDao::toNames);
  }

  private static List<String> toNames(QueryResultResponse<EventCatalogEventNameRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows().stream()
      .map(EventCatalogEventNameRow::getName)
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toList());
  }

  private static List<String> toFilterKeys(QueryResultResponse<EventCatalogFilterKeyRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows().stream()
      .map(EventCatalogFilterKeyRow::getFilterKey)
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toList());
  }
}
