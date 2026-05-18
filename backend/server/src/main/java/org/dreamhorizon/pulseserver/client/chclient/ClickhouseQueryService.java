package org.dreamhorizon.pulseserver.client.chclient;

import com.clickhouse.client.api.insert.InsertResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Statement;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.service.IAnalyticalStoreClient;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageStats;

@Slf4j
@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseQueryService implements IAnalyticalStoreClient<GetRawUserEventsResponseDto> {

  private static final String USE_QUERY_CONDITION_CACHE_SETTINGS =
      " SETTINGS use_query_condition_cache = 1";

  private final ClickhouseReadClient clickhouseReadClient;
  private final ClickhouseWriteClient clickhouseWriteClient;
  private final ClickhouseProjectConnectionPoolManager clickhouseProjectConnectionPoolManager;
  private final ClickhouseProjectCredentialsDao clickhouseProjectCredentialsDao;
  private final ClickhouseConfig clickhouseConfig;
  private final ObjectMapper objectMapper;


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

  // TODO: Combine this with the executeTenantQuery method

  /**
   * Root-cause analysis ClickHouse queries only: runs SQL with named binds ({@code :name}) as required by
   * {@code clickhouse-r2dbc}.
   */
  public Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeRootCauseQuery(
      String projectId, String sql, List<String> bindNames, List<Object> bindValues) {
    return executeRootCauseQuery(projectId, sql, bindNames, bindValues, false);
  }

  public Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeRootCauseQuery(
      String projectId,
      String sql,
      List<String> bindNames,
      List<Object> bindValues,
      boolean useQueryConditionCache) {
    final List<GetRawUserEventsResponseDto.Field> schemaFields = new ArrayList<>();

    if (projectId == null) {
      return Single.error(new IllegalArgumentException("Project ID must be provided - tenant-level access is not allowed"));
    }

    log.debug("Executing root-cause query with named binds for project: {}", projectId);
    List<String> names = bindNames == null ? List.of() : bindNames;
    List<Object> values = bindValues == null ? List.of() : bindValues;
    String sqlToRun =
        resolveSqlForExecution(
            sql, useQueryConditionCache);

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
              return executeTenantQueryWithNamedParameters(pool, sqlToRun, names, values, schemaFields);
            })
        .doOnError(error -> log.error("Error executing root-cause query for project: {}", projectId, error));
  }

  private Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeTenantQuery(
      io.r2dbc.pool.ConnectionPool pool,
      QueryConfiguration queryConfig,
      List<GetRawUserEventsResponseDto.Field> schemaFields) {

    return Single.fromPublisher(pool.create())
        .flatMap(
            conn -> Flowable.fromPublisher(
                    conn.createStatement(
                            resolveSqlForExecution(
                                queryConfig.getQuery(),
                                queryConfig.isUseQueryConditionCache()))
                        .execute())
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

  private Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeTenantQueryWithNamedParameters(
      io.r2dbc.pool.ConnectionPool pool,
      String sql,
      List<String> bindNames,
      List<Object> bindValues,
      List<GetRawUserEventsResponseDto.Field> schemaFields) {

    return Single.fromPublisher(pool.create())
        .flatMap(
            conn -> Flowable.fromPublisher(
                    bindNamedParameters(conn, sql, bindNames, bindValues).execute())
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

  /**
   * Runs SQL with the global ClickHouse user ({@link ClickhouseReadClient} pool). Skips per-project credentials
   * and row policies — SQL must enforce isolation (e.g. literal {@code ProjectId} in {@code WHERE} / {@code INSERT}).
   * Used for server-trusted cache tables so project users do not need {@code INSERT} grants on those tables.
   */
  public <T> Single<QueryResultResponse<T>> executeGenericQueryWithGlobalPool(
      QueryConfiguration queryConfig, Class<T> clazz) {
    log.debug(
        "Executing generic ClickHouse query with global pool (projectId={})",
        queryConfig.getProjectId());
    return executeTenantGenericQuery(clickhouseReadClient.getPool(), queryConfig, clazz);
  }

  /**
   * Like {@link #executeGenericQueryWithGlobalPool} but binds parameters via {@code :name} placeholders
   * (clickhouse-r2dbc), avoiding string concatenation for untrusted values.
   */
  public <T> Single<QueryResultResponse<T>> executeGenericQueryWithGlobalPoolBinds(
      QueryConfiguration queryConfig,
      Class<T> clazz,
      List<String> bindNames,
      List<Object> bindValues) {
    log.debug(
        "Executing generic ClickHouse query with global pool + binds (projectId={})",
        queryConfig.getProjectId());
    String sql =
        resolveSqlForExecution(
            queryConfig.getQuery(), queryConfig.isUseQueryConditionCache());
    return executeTenantGenericQueryWithBinds(
        clickhouseReadClient.getPool(), sql, clazz, bindNames, bindValues);
  }

  /**
   * Same as {@link #executeGenericQueryWithGlobalPool} for statements that return the raw row shape (e.g. {@code
   * INSERT} with no row mapper class).
   */
  public Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeQueryWithGlobalPool(
      QueryConfiguration queryConfig) {
    final List<GetRawUserEventsResponseDto.Field> schemaFields = new ArrayList<>();
    log.debug(
        "Executing ClickHouse query with global pool (projectId={})",
        queryConfig.getProjectId());
    return executeTenantQuery(clickhouseReadClient.getPool(), queryConfig, schemaFields);
  }

  /**
   * Like {@link #executeQueryWithGlobalPool} but with named binds for values that must not be interpolated
   * (e.g. JSON blobs).
   */
  public Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeQueryWithGlobalPoolBinds(
      QueryConfiguration queryConfig, List<String> bindNames, List<Object> bindValues) {
    log.debug(
        "Executing ClickHouse query with global pool + binds (projectId={})",
        queryConfig.getProjectId());
    String sql =
        resolveSqlForExecution(
            queryConfig.getQuery(), queryConfig.isUseQueryConditionCache());
    List<String> names = bindNames == null ? List.of() : bindNames;
    List<Object> values = bindValues == null ? List.of() : bindValues;
    return executeTenantQueryWithNamedParameters(
        clickhouseReadClient.getPool(), sql, names, values, new ArrayList<>());
  }

  private <T> Single<QueryResultResponse<T>> executeTenantGenericQuery(
      io.r2dbc.pool.ConnectionPool pool, QueryConfiguration queryConfig, Class<T> clazz) {
    String sql =
        resolveSqlForExecution(
            queryConfig.getQuery(), queryConfig.isUseQueryConditionCache());
    return executeTenantGenericQueryWithBinds(pool, sql, clazz, List.of(), List.of());
  }

  private <T> Single<QueryResultResponse<T>> executeTenantGenericQueryWithBinds(
      io.r2dbc.pool.ConnectionPool pool,
      String sql,
      Class<T> clazz,
      List<String> bindNames,
      List<Object> bindValues) {
    List<String> names = bindNames == null ? List.of() : bindNames;
    List<Object> values = bindValues == null ? List.of() : bindValues;

    return Single.fromPublisher(pool.create())
        .flatMap(
            conn ->
                Flowable.fromPublisher(bindNamedParameters(conn, sql, names, values).execute())
                    .flatMap(
                        result ->
                            result.map(
                                (row, md) -> {
                                  Map<String, Object> m = new LinkedHashMap<>();
                                  for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                                    Object cell = row.get(i);
                                    m.put(
                                        md.getColumnMetadatas().get(i).getName(),
                                        cell == null ? null : cell.toString());
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
            err ->
                Single.error(new Exception("Failed to execute tenant generic query", err)));
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
    log.info("Fetching current month usage from ClickHouse");

    io.r2dbc.pool.ConnectionPool pool = clickhouseReadClient.getPool();

    return Single.fromPublisher(pool.create())
        .flatMap(connection ->
            Flowable.fromPublisher(
                    connection
                        .createStatement(ProjectUsageLimitQueries.CLICKHOUSE_GET_CURRENT_MONTH_USAGE_BY_PROJECT)
                        .execute())
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

  /**
   * Appends {@link #USE_QUERY_CONDITION_CACHE_SETTINGS} when requested; avoids double-appending if
   * the string already contains {@code use_query_condition_cache}.
   */
  static String resolveSqlForExecution(String sql, boolean useQueryConditionCache) {
    if (sql == null) {
      return null;
    }
    String trimmed = sql.replaceAll("\\s*;\\s*$", "");
    if (!useQueryConditionCache) {
      return trimmed;
    }
    if (trimmed.toLowerCase(Locale.ROOT).contains("use_query_condition_cache")) {
      return trimmed;
    }
    return trimmed + USE_QUERY_CONDITION_CACHE_SETTINGS;
  }

  /**
   * ClickHouse R2DBC expects named parameters ({@code :param} in SQL, {@link Statement#bind(String, Object)}).
   */
  private static Statement bindNamedParameters(
      Connection conn, String sql, List<String> bindNames, List<Object> bindValues) {
    Statement statement = conn.createStatement(sql);
    List<String> names = bindNames == null ? List.of() : bindNames;
    List<Object> values = bindValues == null ? List.of() : bindValues;
    boolean sizesMismatch = names.size() != values.size();
    if (sizesMismatch) {
      throw new IllegalArgumentException("bindNames and bindValues must have the same size");
    }
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      Object value = values.get(i);
      statement = statement.bind(name, value);
    }
    return statement;
  }
}
