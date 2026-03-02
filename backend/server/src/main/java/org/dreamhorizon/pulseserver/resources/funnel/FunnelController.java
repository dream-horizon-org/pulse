package org.dreamhorizon.pulseserver.resources.funnel;

import com.google.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelHealthResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionsRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionsResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.funnel.FunnelService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

@Slf4j
@Path("/v1/funnel")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelController {

  private final FunnelService funnelService;

  @POST
  @Path("/analyze")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<FunnelResponse>> analyzeFunnel(FunnelRequest request) {
    request.setTenantId(TenantContext.requireTenantId());
    return funnelService.analyzeFunnel(request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/health")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<FunnelHealthResponse>> getFunnelHealth(FunnelRequest request) {
    request.setTenantId(TenantContext.requireTenantId());
    return funnelService.getFunnelHealth(request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/sessions")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<FunnelSessionsResponse>> getFunnelSessions(FunnelSessionsRequest request) {
    request.setTenantId(TenantContext.requireTenantId());
    return funnelService.getFunnelSessions(request)
        .to(RestResponse.jaxrsRestHandler());
  }
}
