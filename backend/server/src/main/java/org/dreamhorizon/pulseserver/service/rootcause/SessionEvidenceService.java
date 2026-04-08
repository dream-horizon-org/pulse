package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.SessionEvidenceResult;

public interface SessionEvidenceService {

  /**
   * Get session evidence (session IDs) for an interaction and segment.
   *
   * Finds sessions WORSE THAN the segment itself by comparing to segment deltas.
   *
   * @param projectId project scope
   * @param interactionName the interaction being analyzed
   * @param startTime inclusive window start
   * @param endTime exclusive window end
   * @param segmentDimensions dimension filters from root cause segment
   * @param segmentDeltas metric deltas from root cause analysis (e.g., error_rate, poor_interaction)
   * @param limit max sessions to return (default 5 if null)
   * @return SessionEvidenceResult with list of session IDs
   */
  Single<SessionEvidenceResult> getSessionEvidence(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Map<String, Double> segmentDeltas,
      Integer limit);

  /**
   * Backward compatible overload without deltas.
   */
  Single<SessionEvidenceResult> getSessionEvidence(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Integer limit);
}
