package org.dreamhorizon.pulseserver.dao.rootcause;

import static org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheQueries.INSERT;
import static org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheQueries.SELECT_BY_KEY;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.JobCreationMode;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseCacheDao {

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Reads from root_cause_cache by key. Returns empty if not found.
   */
  public Maybe<RootCauseCacheRow> get(String tenantId, String projectId, String interactionName, String date) {
    String query = String.format(SELECT_BY_KEY,
        escape(tenantId), escape(projectId), escape(interactionName), escape(date));
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .timeoutMs(10000)
        .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .flatMapMaybe(resp -> {
          List<Map<String, Object>> rows = parseRows(resp);
          if (rows.isEmpty()) return Maybe.empty();
          return Maybe.just(mapToRow(rows.get(0)));
        });
  }

  /**
   * Inserts or replaces (ReplacingMergeTree) a cache row.
   */
  public Completable upsert(String tenantId, String projectId, String interactionName, String date,
      String mode, String baselineJson, String segmentsJson) {
    String query = String.format(INSERT,
        escape(tenantId), escape(projectId), escape(interactionName), escape(date),
        escape(mode), escapeJson(baselineJson), escapeJson(segmentsJson));
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .timeoutMs(10000)
        .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .ignoreElement()
        .onErrorResumeNext(e -> {
          log.warn("Root cause cache upsert failed: {}", e.getMessage());
          return Completable.error(e);
        });
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("'", "''").replace("\\", "\\\\");
  }

  private static String escapeJson(String s) {
    if (s == null) return "{}";
    return escape(s);
  }

  private List<Map<String, Object>> parseRows(GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    GetRawUserEventsResponseDto data = response.getData();
    if (data == null || data.getRows() == null) return List.of();
    List<String> fields = data.getSchema().getFields().stream()
        .map(GetRawUserEventsResponseDto.Field::getName)
        .toList();
    return data.getRows().stream()
        .map(row -> {
          Map<String, Object> map = new java.util.LinkedHashMap<>();
          var values = row.getRowFields();
          for (int i = 0; i < fields.size() && i < values.size(); i++) {
            map.put(fields.get(i), values.get(i).getValue());
          }
          return map;
        })
        .toList();
  }

  private static RootCauseCacheRow mapToRow(Map<String, Object> map) {
    Object cachedAt = map.get("cached_at");
    Instant instant = null;
    if (cachedAt != null) {
      try {
        instant = Instant.parse(cachedAt.toString());
      } catch (Exception e) {
        // ignore
      }
    }
    return RootCauseCacheRow.builder()
        .tenantId(toString(map.get("tenant_id")))
        .projectId(toString(map.get("project_id")))
        .interactionName(toString(map.get("interaction_name")))
        .date(toString(map.get("date")))
        .mode(toString(map.get("mode")))
        .baseline(toString(map.get("baseline")))
        .segments(toString(map.get("segments")))
        .cachedAt(instant)
        .build();
  }

  private static String toString(Object o) {
    return o != null ? o.toString() : null;
  }
}
