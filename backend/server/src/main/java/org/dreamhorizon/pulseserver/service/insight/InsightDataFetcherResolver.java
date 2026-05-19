package org.dreamhorizon.pulseserver.service.insight;

import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;

/** Maps an {@link InsightType} to its backing {@link InsightDataFetcher}. */
public interface InsightDataFetcherResolver {
  InsightDataFetcher resolve(InsightType type);
}
