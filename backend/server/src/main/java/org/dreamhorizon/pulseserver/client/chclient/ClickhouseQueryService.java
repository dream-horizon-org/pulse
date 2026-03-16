package org.dreamhorizon.pulseserver.client.chclient;

import com.clickhouse.client.api.insert.InsertResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.service.IAnalyticalStoreClient;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageStats;
import java.util.HashMap;

@Slf4j
@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseQueryService implements IAnalyticalStoreClient<GetRawUserEventsResponseDto> {
  private final ClickhouseReadClient clickhouseReadClient;
  private final ClickhouseWriteClient clickhouseWriteClient;
  private final ClickhouseProjectConnectionPoolManager clickhouseProjectConnectionPoolManager;
  private final ClickhouseProjectCredentialsDao clickhouseProjectCredentialsDao;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());


  @Override
  public Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeQueryOrCreateJob(QueryConfiguration queryConfig) {
    final List<GetRawUserEventsResponseDto.Field> schemaFields = new ArrayList<>();

    // Project-based credentials only - tenant-level access is no longer supported
    String projectId = queryConfig.getProjectId();

    if (projectId != null) {
      log.debug("Executing query using project credentials for project: {}", projectId);
      return clickhouseProjectCredentialsDao
          .getCredentialsByProjectId(projectId)
          .switchIfEmpty(Single.error(new IllegalStateException("No ClickHouse credentials found for project: " + projectId)))
          .flatMap(
              credentials -> {
                var pool =
                    clickhouseProjectConnectionPoolManager.getPoolForProject(
                        projectId,
                        credentials.getClickhouseUsername(),
                        credentials.getClickhousePasswordEncrypted());

                return executeTenantQuery(pool, queryConfig, schemaFields);
              })
          .doOnError(
              error -> log.error("Error executing query for project: {}", projectId, error));
    } else {
      return Single.error(new IllegalArgumentException("Project ID must be provided - tenant-level access is not allowed"));
    }
  }

  private Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeTenantQuery(
      io.r2dbc.pool.ConnectionPool pool,
      QueryConfiguration queryConfig,
      List<GetRawUserEventsResponseDto.Field> schemaFields) {

    return Single.fromPublisher(pool.create())
        .flatMap(
            conn -> Flowable.fromPublisher(
                    conn.createStatement(queryConfig.getQuery()).execute())
                .flatMap(
                    result -> {
                      return result.map(
                          (row, md) -> {
                            if (schemaFields.isEmpty()) {
                              for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                                schemaFields.add(
                                    new GetRawUserEventsResponseDto.Field(
                                        md.getColumnMetadatas().get(i).getName()));
                              }
                            }
                            List<GetRawUserEventsResponseDto.RowField> rowFields =
                                new ArrayList<>();
                            for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                              rowFields.add(
                                  new GetRawUserEventsResponseDto.RowField(row.get(i)));
                            }
                            return new GetRawUserEventsResponseDto.Row(rowFields);
                          });
                    })
                .toList()
                .map(
                    rows -> {
                      GetRawUserEventsResponseDto.Schema schema =
                          new GetRawUserEventsResponseDto.Schema(schemaFields);
                      GetRawUserEventsResponseDto responseData =
                          GetRawUserEventsResponseDto.builder()
                              .schema(schema)
                              .rows(rows)
                              .totalRows((long) rows.size())
                              .build();
                      return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                          .data(responseData)
                          .jobComplete(true)
                          .build();
                    })
                .doFinally(() -> Completable.fromPublisher(conn.close()).subscribe()))
        .onErrorResumeNext(
            err -> Single.error(new Exception("Failed to execute tenant query", err)));
  }

  @Override
  public <T> Single<QueryResultResponse<T>> executeQueryOrCreateJob(QueryConfiguration queryConfig, Class<T> clazz) {
    String projectId = queryConfig.getProjectId();

    if (projectId != null) {
      log.debug("Executing generic query for project: {}", projectId);

      return clickhouseProjectCredentialsDao
          .getCredentialsByProjectId(projectId)
          .switchIfEmpty(Single.error(new IllegalStateException("No ClickHouse credentials found for project: " + projectId)))
          .flatMap(
              credentials -> {
                var pool =
                    clickhouseProjectConnectionPoolManager.getPoolForProject(
                        projectId,
                        credentials.getClickhouseUsername(),
                        credentials.getClickhousePasswordEncrypted());

                log.debug(
                    "Using project pool for {} with user: {}",
                    projectId,
                    credentials.getClickhouseUsername());

                return executeTenantGenericQuery(pool, queryConfig, clazz);
              })
          .doOnError(
              error -> log.error("Error executing generic query for project: {}", projectId, error));
    } else {
      return Single.error(new IllegalArgumentException("Project ID must be provided - tenant-level access is not allowed"));
    }
  }

  private <T> Single<QueryResultResponse<T>> executeTenantGenericQuery(
      io.r2dbc.pool.ConnectionPool pool, QueryConfiguration queryConfig, Class<T> clazz) {

    return Single.fromPublisher(pool.create())
        .flatMap(
            conn ->
                Flowable.fromPublisher(conn.createStatement(queryConfig.getQuery()).execute())
                    .flatMap(
                        result ->
                            result.map(
                                (row, md) -> {
                                  Map<String, Object> m = new LinkedHashMap<>();
                                  for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                                    m.put(
                                        md.getColumnMetadatas().get(i).getName(),
                                        row.get(i).toString());
                                  }
                                  return m;
                                }))
                    .toList()
                    .flatMap(
                        maps -> {
                          List<T> mappedRows = new ArrayList<>(maps.size());
                          for (Map<String, Object> map : maps) {
                            mappedRows.add(objectMapper.convertValue(map, clazz));
                          }
                          return Single.just(
                              QueryResultResponse.<T>builder()
                                  .jobComplete(true)
                                  .rows(mappedRows)
                                  .build());
                        })
                    .doFinally(() -> Completable.fromPublisher(conn.close()).subscribe())
        )
        .onErrorResumeNext(
            err -> {
              return Single.error(new Exception("Failed to execute tenant generic query", err));
            });
  }

  public Single<Long> insertStackTraces(List<StackTraceEvent> events) {
    return clickhouseWriteClient.insert(events)
        .map(InsertResponse::getWrittenRows);
  }

  /**
   * Get current month usage for all projects from ClickHouse.
   * Returns a map of projectId -> UsageStats for easy lookup.
   */
  public Single<Map<String, UsageStats>> getCurrentMonthUsage() {
    String query = """
        SELECT 
            project_id,
            sum(event_count) as events_used,
            uniqCombined64Merge(session_count) as sessions_used
        FROM otel.project_monthly_usage
        WHERE month = toStartOfMonth(now())
        GROUP BY project_id
        """;

    log.info("Fetching current month usage from ClickHouse");

    io.r2dbc.pool.ConnectionPool pool = clickhouseReadClient.getPool();

    return Single.fromPublisher(pool.create())
        .flatMap(connection -> 
            Flowable.fromPublisher(connection.createStatement(query).execute())
                .flatMap(result -> 
                    result.map((row, metadata) -> {
                      String projectId = row.get("project_id", String.class);
                      Long eventsUsed = row.get("events_used", Long.class);
                      Long sessionsUsed = row.get("sessions_used", Long.class);
                      
                      return UsageStats.builder()
                          .projectId(projectId)
                          .eventsUsed(eventsUsed != null ? eventsUsed : 0L)
                          .sessionsUsed(sessionsUsed != null ? sessionsUsed : 0L)
                          .build();
                    })
                )
                .toList()
                .map(statsList -> {
                  Map<String, UsageStats> statsMap = new HashMap<>();
                  for (UsageStats stats : statsList) {
                    statsMap.put(stats.getProjectId(), stats);
                  }
                  return statsMap;
                })
                .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe())
        )
        .doOnSuccess(statsMap -> 
            log.info("✅ Successfully fetched usage stats for {} projects", statsMap.size())
        )
        .doOnError(error -> 
            log.error("❌ Error fetching usage stats from ClickHouse", error)
        );
  }
}
