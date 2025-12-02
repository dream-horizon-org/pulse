package in.horizonos.pulseserver.resources.performance;

import com.google.inject.Inject;
import in.horizonos.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import in.horizonos.pulseserver.resources.performance.models.QueryRequest;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.interaction.PerformanceMetricService;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/v1/interactions/performance-metric/")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class PerformanceMetricDistribution {

  private final PerformanceMetricService performanceMetricService;

  @POST
  @Path("/distribution")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<PerformanceMetricDistributionRes>> getMetricDistribution(QueryRequest request) {
    return performanceMetricService.getMetricDistribution(request)
        .to(RestResponse.jaxrsRestHandler());
  }
}
