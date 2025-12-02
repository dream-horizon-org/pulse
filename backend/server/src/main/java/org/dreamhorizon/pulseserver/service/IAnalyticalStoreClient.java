package org.dreamhorizon.pulseserver.service;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

public interface IAnalyticalStoreClient<T> {
  Single<GetQueryDataResponseDto<T>> executeQueryOrCreateJob(QueryConfiguration queryConfig);

  <S> Single<QueryResultResponse<S>> executeQueryOrCreateJob(QueryConfiguration queryConfig, Class<S> clazz);

}
