package org.dreamhorizon.pulseserver.service.funnel;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.funnel.models.CreateFunnelDefinitionRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelDefinitionListResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelDefinitionResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelListQueryParams;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelResultsResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.UpdateFunnelDefinitionRequest;

public interface FunnelService {

  Single<Long> create(
      String projectId, CreateFunnelDefinitionRequest request, String createdBy);

  Completable update(
      String projectId, long id, UpdateFunnelDefinitionRequest request);

  Completable delete(String projectId, long id);

  Single<FunnelDefinitionResponse> get(String projectId, long id);

  Single<FunnelDefinitionListResponse> list(String projectId, FunnelListQueryParams query);

  /** Latest pre-computed funnel steps from ClickHouse ({@code otel.funnel_results}). */
  Single<FunnelResultsResponse> getResults(String projectId, long id);
}
