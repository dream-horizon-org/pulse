package in.horizonos.pulseserver.service;

import in.horizonos.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import in.horizonos.pulseserver.model.QueryConfiguration;
import in.horizonos.pulseserver.model.QueryResultResponse;
import io.reactivex.rxjava3.core.Single;

public interface IAnalyticalStoreClient<T> {
  Single<GetQueryDataResponseDto<T>> executeQueryOrCreateJob(QueryConfiguration queryConfig);

  <S> Single<QueryResultResponse<S>> executeQueryOrCreateJob(QueryConfiguration queryConfig, Class<S> clazz);

}
