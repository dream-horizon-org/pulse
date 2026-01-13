package org.dreamhorizon.pulseserver.resources.query.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.query.models.CancelQueryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/query")
public class CancelQuery {
  private final QueryService queryService;

  @DELETE
  @Path("/job/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CancelQueryResponseDto>> cancelQuery(@PathParam("jobId") String jobId) {
    return queryService.cancelQuery(jobId)
        .map(this::mapToResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  private CancelQueryResponseDto mapToResponse(org.dreamhorizon.pulseserver.service.query.models.QueryJob job) {
    String message;
    if (job.getStatus() == QueryJobStatus.CANCELLED) {
      message = "Query cancelled successfully";
    } else if (isFinalState(job.getStatus())) {
      message = "Query cannot be cancelled - already in final state: " + job.getStatus().name();
    } else {
      message = "Query cancellation requested";
    }

    return CancelQueryResponseDto.builder()
        .jobId(job.getJobId())
        .status(job.getStatus().name())
        .message(message)
        .queryExecutionId(job.getQueryExecutionId())
        .dataScannedInBytes(job.getDataScannedInBytes())
        .updatedAt(job.getUpdatedAt())
        .build();
  }

  private boolean isFinalState(QueryJobStatus status) {
    return status == QueryJobStatus.COMPLETED
        || status == QueryJobStatus.FAILED
        || status == QueryJobStatus.CANCELLED;
  }
}


