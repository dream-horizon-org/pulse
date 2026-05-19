package org.dreamhorizon.pulseserver.service.insight;

import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.InsightSnapshotDao;

/** Maps an {@link InsightType} to its backing {@link InsightSnapshotDao}. */
public interface InsightSnapshotResolver {
  InsightSnapshotDao resolve(InsightType type);
}
