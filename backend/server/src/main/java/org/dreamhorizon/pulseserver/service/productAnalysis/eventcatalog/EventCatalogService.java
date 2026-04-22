package org.dreamhorizon.pulseserver.service.productAnalysis.eventcatalog;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelEventsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelFilterKeysResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelFilterValuesResponse;

public interface EventCatalogService {

  /**
   * Distinct event names from {@code otel.event_catalog_entries} for {@code FilterKey = EVENT}.
   */
  Single<FunnelEventsResponse> listEventNames(String projectId);

  /**
   * Distinct filter keys from {@code otel.event_catalog_entries}, excluding {@code EVENT}.
   */
  Single<FunnelFilterKeysResponse> listFilterKeys(String projectId);

  /**
   * Distinct {@code FilterValue} rows for {@code projectId} and the given {@code filterKey}.
   */
  Single<FunnelFilterValuesResponse> listFilterValues(String projectId, String filterKey);
}
