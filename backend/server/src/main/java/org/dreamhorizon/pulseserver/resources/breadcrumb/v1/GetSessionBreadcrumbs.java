package org.dreamhorizon.pulseserver.resources.breadcrumb.v1;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.breadcrumb.models.BreadcrumbRequestDto;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.breadcrumb.BreadcrumbService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/breadcrumbs")
public class GetSessionBreadcrumbs {
  private final BreadcrumbService breadcrumbService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SubmitQueryResponseDto>> getSessionBreadcrumbs(
      @HeaderParam("user-email") String userEmail,
      @Valid BreadcrumbRequestDto request) {
    return breadcrumbService.getSessionBreadcrumbs(
            request.getSessionId(), request.getErrorTimestamp(), userEmail)
        .map(job -> {
          if (job.getStatus() == QueryJobStatus.COMPLETED) {
            if (job.getResultData() != null) {
              return SubmitQueryResponseDto.builder()
                  .jobId(job.getJobId())
                  .status("COMPLETED")
                  .message("Breadcrumbs fetched successfully")
                  .resultData(job.getResultData())
                  .nextToken(job.getNextToken())
                  .dataScannedInBytes(job.getDataScannedInBytes())
                  .createdAt(job.getCreatedAt())
                  .completedAt(job.getCompletedAt())
                  .build();
            } else {
              return SubmitQueryResponseDto.builder()
                  .jobId(job.getJobId())
                  .status("COMPLETED")
                  .message("Query completed but results are not available yet. Use GET /query/job/{jobId} to fetch results.")
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
                .message(job.getErrorMessage() != null ? job.getErrorMessage() : "Breadcrumb query " + job.getStatus().name().toLowerCase())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
          } else {
            return SubmitQueryResponseDto.builder()
                .jobId(job.getJobId())
                .status(job.getStatus().name())
                .message("Breadcrumb query submitted. Use GET /query/job/{jobId} to check status and get results.")
                .createdAt(job.getCreatedAt())
                .build();
          }
        })
        .to(RestResponse.jaxrsRestHandler());
  }
}
