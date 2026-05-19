package org.dreamhorizon.pulseserver.service.insight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightDateRangeResolver {

  private final InsightSnapshotResolver snapshotResolver;

  /**
   * Returns the list of dates in [startDate, endDate] that do NOT have a cached snapshot.
   * When regenerate=true, returns ALL dates (force recompute).
   */
  public Single<List<LocalDate>> resolveMissingDates(
      final String projectId,
      final String entityKey,
      final LocalDate startDate,
      final LocalDate endDate,
      final boolean regenerate,
      final InsightType insightType) {
    List<LocalDate> allDates = enumerateDates(startDate, endDate);
    if (regenerate) {
      return Single.just(allDates);
    }
    return snapshotResolver.resolve(insightType)
        .getExistingDates(projectId, insightType, entityKey, allDates)
        .map(
            existingDates -> {
              List<LocalDate> missing = new ArrayList<>();
              for (LocalDate d : allDates) {
                if (!existingDates.contains(d)) {
                  missing.add(d);
                }
              }
              return missing;
            });
  }

  public static List<LocalDate> enumerateDates(final LocalDate startDate, final LocalDate endDate) {
    List<LocalDate> dates = new ArrayList<>();
    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      dates.add(current);
      current = current.plusDays(1);
    }
    return dates;
  }
}
