package org.dreamhorizon.pulseserver.dao.insightsnapshot;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseWriteClient;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.models.DailySnapshot;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;

/** Shared ClickHouse store for per-day raw insight metrics (all {@link InsightType}s). */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightDailySnapshotDao implements InsightSnapshotDao {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final ClickhouseQueryService clickhouseQueryService;
  private final ClickhouseWriteClient clickhouseWriteClient;

  @Override
  public Single<Set<LocalDate>> getExistingDates(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) {
      return Single.just(new HashSet<>());
    }
    String inClause = buildDateInClause(dates);
    String sql = String.format(
        InsightDailySnapshotQueries.SELECT_EXISTING_DATES,
        escape(projectId),
        escape(insightType.name()),
        escape(entityKey),
        inClause);
    QueryConfiguration config = QueryConfiguration.newQuery(sql)
        .projectId(projectId)
        .useQueryConditionCache(false)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .map(
            response -> {
              Set<LocalDate> result = new HashSet<>();
              if (response == null || response.getData() == null
                  || response.getData().getRows() == null) {
                return result;
              }
              for (var row : response.getData().getRows()) {
                if (row.getRowFields() == null || row.getRowFields().isEmpty()) {
                  continue;
                }
                Object val = row.getRowFields().get(0).getValue();
                if (val != null) {
                  try {
                    result.add(LocalDate.parse(val.toString(), DATE_FMT));
                  } catch (Exception e) {
                    log.warn("Failed to parse snapshot date '{}': {}", val, e.getMessage());
                  }
                }
              }
              return result;
            })
        .doOnError(
            e -> log.warn(
                "insight snapshot getExistingDates failed project={} type={}: {}",
                projectId, insightType, e.getMessage()));
  }

  @Override
  public Single<List<DailySnapshot>> getSnapshotsForDates(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) {
      return Single.just(List.of());
    }
    String inClause = buildDateInClause(dates);
    String sql = String.format(
        InsightDailySnapshotQueries.SELECT_SNAPSHOTS_FOR_DATES,
        escape(projectId),
        escape(insightType.name()),
        escape(entityKey),
        inClause);
    QueryConfiguration config = QueryConfiguration.newQuery(sql)
        .projectId(projectId)
        .useQueryConditionCache(false)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .map(
            response -> {
              List<DailySnapshot> result = new ArrayList<>();
              if (response == null || response.getData() == null
                  || response.getData().getRows() == null) {
                return result;
              }
              for (var row : response.getData().getRows()) {
                List<org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto.RowField> fields =
                    row.getRowFields();
                if (fields == null || fields.size() < 2) {
                  continue;
                }
                Object dateVal = fields.get(0).getValue();
                Object dataVal = fields.get(1).getValue();
                if (dateVal == null) {
                  continue;
                }
                try {
                  LocalDate snapshotDate = LocalDate.parse(dateVal.toString(), DATE_FMT);
                  String computedData = dataVal != null ? dataVal.toString() : "{}";
                  result.add(new DailySnapshot(snapshotDate, computedData));
                } catch (Exception e) {
                  log.warn("Failed to parse insight snapshot row: {}", e.getMessage());
                }
              }
              return result;
            })
        .doOnError(
            e -> log.warn(
                "insight snapshot getSnapshotsForDates failed project={} type={}: {}",
                projectId, insightType, e.getMessage()));
  }

  @Override
  public Completable upsert(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final LocalDate date,
      final String computedData) {
    String sql = buildInsertSql(projectId, insightType, entityKey, date, computedData);
    return clickhouseWriteClient
        .executeSql(sql)
        .ignoreElement()
        .doOnError(
            e -> log.warn(
                "insight snapshot upsert failed project={} type={} date={}: {}",
                projectId, insightType, date, e.getMessage()));
  }

  private static String buildInsertSql(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final LocalDate date,
      final String computedData) {
    String escapedProject = escape(projectId);
    String escapedType = escape(insightType.name());
    String escapedEntity = escape(entityKey);
    String dateStr = date.format(DATE_FMT);
    String escapedData = escape(computedData != null ? computedData : "{}");
    return "INSERT INTO otel.insight_daily_snapshots"
        + " (ProjectId, InsightType, EntityKey, SnapshotDate, ComputedData, ComputedAt)"
        + " VALUES ("
        + "'" + escapedProject + "',"
        + "'" + escapedType + "',"
        + "'" + escapedEntity + "',"
        + "'" + dateStr + "',"
        + "'" + escapedData + "',"
        + "now64(3)"
        + ")";
  }

  private static String buildDateInClause(final List<LocalDate> dates) {
    return dates.stream()
        .map(d -> "'" + d.format(DATE_FMT) + "'")
        .collect(Collectors.joining(", "));
  }

  private static String escape(final String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
