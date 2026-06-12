package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.*;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelJourneyTagsListResponse;

public interface FunnelService {

  Single<Long> create(
    String projectId, CreateFunnelDefinitionRequest request, String createdBy);

  Completable update(
    String projectId, long id, UpdateFunnelDefinitionRequest request);

  Completable delete(String projectId, long id);

  /**
   * Stops auto-refresh for an AUTO funnel by flipping its type to ONCE and freezing the
   * analysis window. The cron's {@code listAllAuto()} no longer picks it up, and the
   * computed-status CASE renders it as COMPLETED. Idempotent — calling on an already-stopped
   * funnel is a no-op (DAO returns 0 rows).
   */
  Completable stopAuto(String projectId, long id);

  Single<FunnelDefinitionResponse> get(String projectId, long id);

  Single<FunnelDefinitionListResponse> list(String projectId, FunnelListQueryParams query);

  /**
   * Latest pre-computed funnel steps from ClickHouse ({@code otel.funnel_results}).
   */
  Single<FunnelResultsResponse> getResults(String projectId, long id);

  /** Replaces tag mappings for the funnel ({@code funnel_journey_tag}). */
  Completable replaceTags(String projectId, long funnelId, List<String> tags);

  /**
   * Distinct tags in the project from {@code funnel_journey_tag} (funnels and journeys combined).
   */
  Single<FunnelJourneyTagsListResponse> listDistinctTags(String projectId);
}
