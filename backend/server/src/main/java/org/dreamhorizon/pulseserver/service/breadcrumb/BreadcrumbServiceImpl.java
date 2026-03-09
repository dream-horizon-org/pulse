package org.dreamhorizon.pulseserver.service.breadcrumb;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class BreadcrumbServiceImpl implements BreadcrumbService {

  private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
  private static final long WINDOW_BEFORE_MS = 10 * 60 * 1000L;
  private static final long WINDOW_AFTER_MS = 30 * 1000L;
  private static final int RESULT_LIMIT = 100;

  private static final DateTimeFormatter SQL_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter PARTITION_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter PARTITION_HOUR_FORMAT =
      DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC);

  private final QueryService queryService;
  private final AthenaConfig athenaConfig;

  @Override
  public Single<SubmitQueryResponseDto> getSessionBreadcrumbs(
      String sessionId, String errorTimestamp, String userEmail) {
    if (sessionId == null || sessionId.isBlank()) {
      return Single.error(new IllegalArgumentException("Session ID is required"));
    }

    if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
      return Single.error(new IllegalArgumentException(
          "Session ID contains invalid characters. Only alphanumeric, dots, hyphens, and underscores are allowed."));
    }

    if (errorTimestamp == null || errorTimestamp.isBlank()) {
      return Single.error(new IllegalArgumentException("Error timestamp is required"));
    }

    Instant errorInstant;
    try {
      errorInstant = Instant.parse(errorTimestamp);
    } catch (DateTimeParseException e) {
      return Single.error(new IllegalArgumentException(
          "Invalid error timestamp format. Expected ISO 8601 (e.g. 2026-02-27T15:14:26Z)."));
    }

    if (userEmail == null || userEmail.isBlank()) {
      return Single.error(new IllegalArgumentException("User email is required"));
    }

    String sql = buildQuery(sessionId, errorInstant);
    log.debug("Breadcrumb query for session {}: {}", sessionId, sql);
    return queryService.submitQuery(sql, userEmail)
        .map(this::mapToResponse);
  }

  SubmitQueryResponseDto mapToResponse(QueryJob job) {
    if (job.getStatus() == QueryJobStatus.COMPLETED) {
      return mapCompletedResponse(job);
    } else if (job.getStatus() == QueryJobStatus.FAILED
        || job.getStatus() == QueryJobStatus.CANCELLED) {
      return mapFailedOrCancelledResponse(job);
    } else {
      return mapInProgressResponse(job);
    }
  }

  SubmitQueryResponseDto mapCompletedResponse(QueryJob job) {
    SubmitQueryResponseDto.SubmitQueryResponseDtoBuilder builder =
        SubmitQueryResponseDto.builder()
            .jobId(job.getJobId())
            .status("COMPLETED")
            .dataScannedInBytes(job.getDataScannedInBytes())
            .createdAt(job.getCreatedAt())
            .completedAt(job.getCompletedAt());

    if (job.getResultData() != null) {
      builder
          .message("Breadcrumbs fetched successfully")
          .resultData(job.getResultData())
          .nextToken(job.getNextToken());
    } else {
      builder
          .message("Query completed but results are not available yet."
              + " Use GET /query/job/{jobId} to fetch results.")
          .resultData(null);
    }
    return builder.build();
  }

  SubmitQueryResponseDto mapFailedOrCancelledResponse(QueryJob job) {
    String message = job.getErrorMessage() != null
        ? job.getErrorMessage()
        : "Breadcrumb query "
            + job.getStatus().name().toLowerCase();

    return SubmitQueryResponseDto.builder()
        .jobId(job.getJobId())
        .status(job.getStatus().name())
        .message(message)
        .createdAt(job.getCreatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }

  SubmitQueryResponseDto mapInProgressResponse(QueryJob job) {
    return SubmitQueryResponseDto.builder()
        .jobId(job.getJobId())
        .status(job.getStatus().name())
        .message("Breadcrumb query submitted."
            + " Use GET /query/job/{jobId}"
            + " to check status and get results.")
        .createdAt(job.getCreatedAt())
        .build();
  }

  String buildQuery(String sessionId, Instant errorInstant) {
    String tenantId = TenantContext.requireTenantId();
    String database = athenaConfig.getDatabase();
    String escapedSessionId = sessionId.replace("'", "''");

    Instant start = errorInstant.minusMillis(WINDOW_BEFORE_MS);
    Instant end = errorInstant.plusMillis(WINDOW_AFTER_MS);

    String startDateStr = PARTITION_DATE_FORMAT.format(start);
    String endDateStr = PARTITION_DATE_FORMAT.format(end);
    String startTimestamp = SQL_TIMESTAMP_FORMAT.format(start);
    String endTimestamp = SQL_TIMESTAMP_FORMAT.format(end);

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT event_name, \"timestamp\", screen_name, props ");
    sb.append("FROM ").append(database).append(".otel_data_").append(tenantId).append(" ");
    sb.append("WHERE session_id = '").append(escapedSessionId).append("' ");

    if (startDateStr.equals(endDateStr)) {
      sb.append("AND date = '").append(startDateStr).append("' ");
      sb.append("AND hour >= '").append(PARTITION_HOUR_FORMAT.format(start)).append("' ");
      sb.append("AND hour <= '").append(PARTITION_HOUR_FORMAT.format(end)).append("' ");
    } else {
      sb.append("AND date >= '").append(startDateStr).append("' ");
      sb.append("AND date <= '").append(endDateStr).append("' ");
    }

    sb.append("AND \"timestamp\" >= TIMESTAMP '").append(startTimestamp).append("' ");
    sb.append("AND \"timestamp\" <= TIMESTAMP '").append(endTimestamp).append("' ");
    sb.append("ORDER BY \"timestamp\" ASC LIMIT ").append(RESULT_LIMIT);

    return sb.toString();
  }
}
