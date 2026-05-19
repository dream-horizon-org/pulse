package org.dreamhorizon.pulseserver.service.insight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.service.insight.anr.AnrDataFetcher;

/** Routes each {@link InsightType} to its backing {@link InsightDataFetcher}. Add new cases here. */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightTypeDataFetcherResolver implements InsightDataFetcherResolver {

  private final AnrDataFetcher anrDataFetcher;

  @Override
  public InsightDataFetcher resolve(final InsightType type) {
    return switch (type) {
      case ANR -> anrDataFetcher;
    };
  }
}
