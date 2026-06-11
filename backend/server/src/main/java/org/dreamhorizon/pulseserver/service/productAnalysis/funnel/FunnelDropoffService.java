package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffEvidenceResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffResponse;

/**
 * Service for the funnel drop-off attribution side-panel.
 *
 * <p>Two operations:
 * <ul>
 *   <li>{@link #getDropoff} — ranked causes for a (funnel × step) at the latest run
 *       (or a caller-supplied run).</li>
 *   <li>{@link #getEvidence} — hydrates the drill-in for a specific cause.</li>
 * </ul>
 */
public interface FunnelDropoffService {

  /**
   * Ranked drop-off causes for one step of a funnel. Throws
   * {@code FUNNEL_NOT_FOUND} if the funnel is missing and
   * {@code FUNNEL_STEP_OUT_OF_RANGE} if {@code stepIndex} is outside the funnel's steps.
   */
  Single<FunnelDropoffResponse> getDropoff(
      String projectId, long funnelId, int stepIndex, String runTime);

  /**
   * Loads per-session context for a set of example session IDs that came from
   * a {@link FunnelDropoffResponse} cause row.
   */
  Single<FunnelDropoffEvidenceResponse> getEvidence(
      String projectId, long funnelId, int stepIndex, String runTime,
      List<String> sessionIds);
}
