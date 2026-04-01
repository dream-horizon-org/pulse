package org.dreamhorizon.pulseserver.service.eventcatalog.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.eventcatalog.EventCatalogDao;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelEventsResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelFilterValuesResponse;
import org.dreamhorizon.pulseserver.service.eventcatalog.EventCatalogService;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EventCatalogServiceImpl implements EventCatalogService {

  private final EventCatalogDao eventCatalogDao;

  @Override
  public Single<FunnelEventsResponse> listEventNames(String projectId) {
    return eventCatalogDao
        .listEventNames(projectId)
        .flatMap(
            events ->
                eventCatalogDao
                    .listFilterKeys(projectId)
                    .map(
                        filterKeys ->
                            FunnelEventsResponse.builder()
                                .events(events)
                                .filterKeys(filterKeys)
                                .build()));
  }

  @Override
  public Single<FunnelFilterValuesResponse> listFilterValues(String projectId, String filterKey) {
    return eventCatalogDao
        .listFilterValues(projectId, filterKey)
        .map(values -> FunnelFilterValuesResponse.builder().values(values).build());
  }
}
