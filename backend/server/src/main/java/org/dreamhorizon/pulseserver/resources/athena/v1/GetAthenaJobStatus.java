package org.dreamhorizon.pulseserver.resources.athena.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.athena.models.GetJobStatusResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.athena.AthenaService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/athena")
public class GetAthenaJobStatus {
  private final AthenaService athenaService;

  @GET
  @Path("/job/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GetJobStatusResponseDto>> getJobStatus(
      @PathParam("jobId") String jobId,
      @QueryParam("maxResults") @DefaultValue("1000") Integer maxResults,
      @QueryParam("nextToken") String nextToken) {
    
    if (nextToken != null && nextToken.contains(" ") && !nextToken.contains("+")) {
      nextToken = nextToken.replace(" ", "+");
      log.debug("Fixed nextToken: replaced spaces with +");
    }
    
    return athenaService.getJobStatus(jobId, maxResults, nextToken)
        .map(this::mapToResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  private GetJobStatusResponseDto mapToResponse(org.dreamhorizon.pulseserver.service.athena.models.AthenaJob job) {
    return GetJobStatusResponseDto.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus().name())
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .resultData(job.getResultData())
        .nextToken(job.getNextToken())
        .dataScannedInBytes(job.getDataScannedInBytes())
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }
}


