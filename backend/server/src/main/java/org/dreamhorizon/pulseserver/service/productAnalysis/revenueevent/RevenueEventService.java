package org.dreamhorizon.pulseserver.service.productAnalysis.revenueevent;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.CreateRevenueEventRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.RevenueEventListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.RevenueEventResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.UpdateRevenueEventRequest;

public interface RevenueEventService {

  Single<RevenueEventListResponse> list(String projectId);

  Single<RevenueEventResponse> create(
    String projectId, CreateRevenueEventRequest request, String configuredBy);

  Completable update(
    String projectId, String id, UpdateRevenueEventRequest request, String configuredBy);

  Completable delete(String projectId, String id);
}
