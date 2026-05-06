package org.dreamhorizon.pulseserver.service.productAnalysis.journey;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.*;

public interface JourneyService {

  Single<Long> create(String projectId, CreateJourneyRequest request, String createdBy);

  Completable update(String projectId, long id, UpdateJourneyRequest request);

  Completable delete(String projectId, long id);

  /**
   * Stops auto-refresh for an AUTO journey by flipping its type to ONCE and freezing the
   * analysis window. Mirrors {@code FunnelService.stopAuto}: idempotent, the cron's
   * {@code listAllAuto()} stops picking it up, and the listing renders the row as COMPLETED.
   */
  Completable stopAuto(String projectId, long id);

  Single<JourneyResponse> get(String projectId, long id);

  Single<JourneyListResponse> list(String projectId, JourneyListQueryParams query);

  /** Replaces tag mappings for the journey ({@code funnel_journey_tag}). */
  Completable replaceTags(String projectId, long journeyId, List<String> tags);
}
