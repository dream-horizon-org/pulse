package org.dreamhorizon.pulseserver.service.productAnalysis.eventcatalog.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.EventCatalogDao;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelEventsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelFilterKeysResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelFilterValuesResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelScreensResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.eventcatalog.EventCatalogService;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EventCatalogServiceImpl implements EventCatalogService {

  private final EventCatalogDao eventCatalogDao;

  @Override
  public Single<FunnelEventsResponse> listEventNames(String projectId) {
    return eventCatalogDao
      .listEventNames(projectId)
      .map(events -> FunnelEventsResponse.builder().events(events).build());
  }

  @Override
  public Single<FunnelScreensResponse> listScreenNames(String projectId) {
    return eventCatalogDao
      .listScreenNames(projectId)
      .map(screens -> FunnelScreensResponse.builder().screens(screens).build());
  }

  @Override
  public Single<FunnelFilterKeysResponse> listFilterKeys(String projectId) {
    return eventCatalogDao
      .listFilterKeys(projectId)
      .map(filterKeys -> FunnelFilterKeysResponse.builder().filters(filterKeys).build());
  }

  @Override
  public Single<FunnelFilterValuesResponse> listFilterValues(String projectId, String filterKey) {
    return eventCatalogDao
      .listFilterValues(projectId, filterKey)
      .map(values -> FunnelFilterValuesResponse.builder().values(values).build());
  }
}
