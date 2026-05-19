package org.dreamhorizon.pulseserver.dao.insightsnapshot;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.models.DailySnapshot;

/** Per-day raw metrics cache in ClickHouse ({@code otel.insight_daily_snapshots}). */
public interface InsightSnapshotDao {

  Single<Set<LocalDate>> getExistingDates(
      String projectId, InsightType insightType, String entityKey, List<LocalDate> dates);

  Single<List<DailySnapshot>> getSnapshotsForDates(
      String projectId, InsightType insightType, String entityKey, List<LocalDate> dates);

  Completable upsert(
      String projectId,
      InsightType insightType,
      String entityKey,
      LocalDate date,
      String computedData);
}
