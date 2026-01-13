package org.dreamhorizon.pulseserver.resources.query.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.query.models.QueryHistoryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/query")
public class GetQueryHistory {
  private final QueryService queryService;

  @GET
  @Path("/history")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<QueryHistoryResponseDto>> getQueryHistory(
      @HeaderParam("user-email") String userEmail,
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset) {
    return queryService.getQueryHistory(userEmail, limit, offset)
        .map(jobs -> {
          List<QueryHistoryResponseDto.QueryHistoryItem> items = jobs.stream()
              .map(this::mapToHistoryItem)
              .collect(Collectors.toList());

          return QueryHistoryResponseDto.builder()
              .queries(items)
              .total(items.size())
              .limit(limit != null ? limit : 20)
              .offset(offset != null ? offset : 0)
              .build();
        })
        .to(RestResponse.jaxrsRestHandler());
  }

  private QueryHistoryResponseDto.QueryHistoryItem mapToHistoryItem(QueryJob job) {
    return QueryHistoryResponseDto.QueryHistoryItem.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .originalQueryString(job.getOriginalQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus() != null ? job.getStatus().name() : null)
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .dataScannedInBytes(job.getDataScannedInBytes())
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }
}

