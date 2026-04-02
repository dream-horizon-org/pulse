package org.dreamhorizon.pulseserver.service.productAnalysis.journey;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.*;

public interface JourneyService {

  Single<Long> create(String projectId, CreateJourneyRequest request, String createdBy);

  Completable update(String projectId, long id, UpdateJourneyRequest request);

  Completable delete(String projectId, long id);

  Single<JourneyResponse> get(String projectId, long id);

  Single<JourneyListResponse> list(String projectId, JourneyListQueryParams query);
}
