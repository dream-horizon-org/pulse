package org.dreamhorizon.pulseserver.resources.query.v1;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryRequestDto;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/query")
public class SubmitQuery {
  private final QueryService queryService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SubmitQueryResponseDto>> submitQuery(@Valid SubmitQueryRequestDto request) {
    return queryService.submitQuery(request.getQueryString(), request.getParameters(), request.getTimestamp())
        .map(job -> {
          if (job.getStatus() == QueryJobStatus.COMPLETED) {
            if (job.getResultData() != null) {
              return SubmitQueryResponseDto.builder()
                  .jobId(job.getJobId())
                  .status("COMPLETED")
                  .message("Query completed successfully within 3 seconds")
                  .queryExecutionId(job.getQueryExecutionId())
                  .resultLocation(job.getResultLocation())
                  .resultData(job.getResultData())
                  .nextToken(job.getNextToken())
                  .dataScannedInBytes(job.getDataScannedInBytes())
                  .createdAt(job.getCreatedAt())
                  .completedAt(job.getCompletedAt())
                  .build();
            } else {
              log.warn("Query completed within 3 seconds for job {} but results are null", job.getJobId());
              return SubmitQueryResponseDto.builder()
                  .jobId(job.getJobId())
                  .status("COMPLETED")
                  .message("Query completed within 3 seconds but results are not available yet. Use GET /query/job/{jobId} to fetch results.")
                  .queryExecutionId(job.getQueryExecutionId())
                  .resultLocation(job.getResultLocation())
                  .resultData(null)
                  .dataScannedInBytes(job.getDataScannedInBytes())
                  .createdAt(job.getCreatedAt())
                  .completedAt(job.getCompletedAt())
                  .build();
            }
          } else if (job.getStatus() == QueryJobStatus.FAILED
              || job.getStatus() == QueryJobStatus.CANCELLED) {
            return SubmitQueryResponseDto.builder()
                .jobId(job.getJobId())
                .status(job.getStatus().name())
                .message(job.getErrorMessage() != null ? job.getErrorMessage() : "Query " + job.getStatus().name().toLowerCase())
                .queryExecutionId(job.getQueryExecutionId())
                .resultLocation(job.getResultLocation())
                .dataScannedInBytes(job.getDataScannedInBytes())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
          } else {
            return SubmitQueryResponseDto.builder()
                .jobId(job.getJobId())
                .status(job.getStatus().name())
                .message("Query submitted successfully. Use GET /query/job/{jobId} to check status and get results.")
                .queryExecutionId(job.getQueryExecutionId())
                .dataScannedInBytes(job.getDataScannedInBytes())
                .createdAt(job.getCreatedAt())
                .build();
          }
        })
        .to(RestResponse.jaxrsRestHandler());
  }
}

