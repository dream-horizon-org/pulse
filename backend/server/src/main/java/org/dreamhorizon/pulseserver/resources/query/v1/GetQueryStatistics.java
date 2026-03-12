package org.dreamhorizon.pulseserver.resources.query.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.query.models.QueryStatisticsResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.query.QueryStatisticsService;
import org.dreamhorizon.pulseserver.service.query.models.QueryStatistics;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/query")
public class GetQueryStatistics {
  private final QueryStatisticsService queryStatisticsService;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  @GET
  @Path("/stats")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<QueryStatisticsResponseDto>> getQueryStatistics(
      @HeaderParam("user-email") String userEmail,
      @QueryParam("startDate") String startDateStr,
      @QueryParam("endDate") String endDateStr) {
    LocalDateTime startDate = parseDate(startDateStr);
    LocalDateTime endDate = parseDate(endDateStr);

    return queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate)
        .map(this::mapToResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  private LocalDateTime parseDate(String dateStr) {
    if (dateStr == null || dateStr.trim().isEmpty()) {
      return null;
    }
    try {
      return LocalDateTime.parse(dateStr, DATE_TIME_FORMATTER);
    } catch (Exception e) {
      log.warn("Failed to parse date: {}, error: {}", dateStr, e.getMessage());
      return null;
    }
  }

  private QueryStatisticsResponseDto mapToResponse(QueryStatistics stats) {
    return QueryStatisticsResponseDto.builder()
        .userEmail(stats.getUserEmail())
        .period(QueryStatisticsResponseDto.Period.builder()
            .startDate(stats.getPeriod().getStartDate() != null 
                ? stats.getPeriod().getStartDate().format(DATE_TIME_FORMATTER) 
                : null)
            .endDate(stats.getPeriod().getEndDate() != null 
                ? stats.getPeriod().getEndDate().format(DATE_TIME_FORMATTER) 
                : null)
            .build())
        .summary(QueryStatisticsResponseDto.QueryStatisticsSummary.builder()
            .totalQueries(stats.getSummary().getTotalQueries())
            .succeeded(stats.getSummary().getSucceeded())
            .failed(stats.getSummary().getFailed())
            .cancelled(stats.getSummary().getCancelled())
            .running(stats.getSummary().getRunning())
            .build())
        .dataStatistics(QueryStatisticsResponseDto.DataStatistics.builder()
            .totalDataScannedBytes(stats.getDataStatistics().getTotalDataScannedBytes())
            .totalDataScannedGB(stats.getDataStatistics().getTotalDataScannedGB())
            .averageDataScannedBytes(stats.getDataStatistics().getAverageDataScannedBytes())
            .maxDataScannedBytes(stats.getDataStatistics().getMaxDataScannedBytes())
            .minDataScannedBytes(stats.getDataStatistics().getMinDataScannedBytes())
            .build())
        .timeStatistics(QueryStatisticsResponseDto.TimeStatistics.builder()
            .totalExecutionTimeMillis(stats.getTimeStatistics().getTotalExecutionTimeMillis())
            .totalExecutionTimeSeconds(stats.getTimeStatistics().getTotalExecutionTimeSeconds())
            .averageExecutionTimeMillis(stats.getTimeStatistics().getAverageExecutionTimeMillis())
            .maxExecutionTimeMillis(stats.getTimeStatistics().getMaxExecutionTimeMillis())
            .minExecutionTimeMillis(stats.getTimeStatistics().getMinExecutionTimeMillis())
            .build())
        .queries(stats.getQueries().stream()
            .map(item -> QueryStatisticsResponseDto.QueryStatisticItem.builder()
                .jobId(item.getJobId())
                .queryExecutionId(item.getQueryExecutionId())
                .status(item.getStatus())
                .dataScannedInBytes(item.getDataScannedInBytes())
                .executionTimeMillis(item.getExecutionTimeMillis())
                .engineExecutionTimeMillis(item.getEngineExecutionTimeMillis())
                .queryQueueTimeMillis(item.getQueryQueueTimeMillis())
                .createdAt(item.getCreatedAt())
                .completedAt(item.getCompletedAt())
                .build())
            .toList())
        .build();
  }
}

