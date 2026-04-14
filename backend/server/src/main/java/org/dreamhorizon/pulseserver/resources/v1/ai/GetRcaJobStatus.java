package org.dreamhorizon.pulseserver.resources.v1.ai;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.v1.ai.models.GetRcaJobResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;

@Slf4j
@Path("/v1/ai-rca")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GetRcaJobStatus {

  private static final String PROJECT_ID_HEADER = "X-Project-ID";

  private final RcaReportJobService rcaReportJobService;

  @GET
  @Path("/job/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetRcaJobResponse>> getJobStatus(
      @PathParam("jobId") String jobId,
      @HeaderParam(PROJECT_ID_HEADER) String projectId) {
    return rcaReportJobService
        .getJobStatus(jobId, projectId)
        .to(RestResponse.jaxrsRestHandler());
  }
}
