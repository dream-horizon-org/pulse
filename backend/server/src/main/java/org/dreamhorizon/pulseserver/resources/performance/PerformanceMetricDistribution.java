package org.dreamhorizon.pulseserver.resources.performance;

import com.google.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

@Slf4j
@Path("/v1/interactions/performance-metric/")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class PerformanceMetricDistribution {

  private final PerformanceMetricService performanceMetricService;

  @POST
  @Path("/distribution")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<PerformanceMetricDistributionRes>> getMetricDistribution(QueryRequest request) {
    request.setTenantId(TenantContext.requireTenantId());
    return performanceMetricService.getMetricDistribution(request)
        .to(RestResponse.jaxrsRestHandler());
  }
}
