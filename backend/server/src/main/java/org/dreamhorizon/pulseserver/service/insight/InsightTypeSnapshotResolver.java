package org.dreamhorizon.pulseserver.service.insight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.InsightSnapshotDao;

/** Returns the shared {@link InsightSnapshotDao} for all insight types. */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightTypeSnapshotResolver implements InsightSnapshotResolver {

  private final InsightSnapshotDao insightDailySnapshotDao;

  @Override
  public InsightSnapshotDao resolve(final InsightType type) {
    return insightDailySnapshotDao;
  }
}
