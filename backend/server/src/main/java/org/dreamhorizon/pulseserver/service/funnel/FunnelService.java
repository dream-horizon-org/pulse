package org.dreamhorizon.pulseserver.service.funnel;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelHealthResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionsRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionsResponse;

public interface FunnelService {
  Single<FunnelResponse> analyzeFunnel(FunnelRequest request);

  Single<FunnelHealthResponse> getFunnelHealth(FunnelRequest request);

  Single<FunnelSessionsResponse> getFunnelSessions(FunnelSessionsRequest request);
}
