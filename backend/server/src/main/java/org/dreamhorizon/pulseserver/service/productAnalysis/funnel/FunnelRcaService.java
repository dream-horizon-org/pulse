package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;

/** Tabular funnel RCA from {@code otel.funnel_dropoff_attribution} only. */
public interface FunnelRcaService {

  /**
   * Builds root-cause payload for one funnel step's drop-off.
   *
   * @param focusStepIndex zero-based step the user dropped from (UI step bar index)
   * @param runTime optional funnel compute run; {@code null} uses latest
   */
  Single<RootCauseResult> getFunnelRootCause(
      String projectId, long funnelId, int focusStepIndex, String runTime);
}
