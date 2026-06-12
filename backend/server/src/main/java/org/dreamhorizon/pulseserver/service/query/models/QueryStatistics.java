package org.dreamhorizon.pulseserver.service.query.models;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryStatistics {
  private String userEmail;
  private Period period;
  private QueryStatisticsSummary summary;
  private DataStatistics dataStatistics;
  private TimeStatistics timeStatistics;
  private List<QueryStatisticItem> queries;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Period {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class QueryStatisticsSummary {
    private int totalQueries;
    private int succeeded;
    private int failed;
    private int cancelled;
    private int running;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DataStatistics {
    private Long totalDataScannedBytes;
    private Double totalDataScannedGB;
    private Long averageDataScannedBytes;
    private Long maxDataScannedBytes;
    private Long minDataScannedBytes;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TimeStatistics {
    private Long totalExecutionTimeMillis;
    private Long totalExecutionTimeSeconds;
    private Long averageExecutionTimeMillis;
    private Long maxExecutionTimeMillis;
    private Long minExecutionTimeMillis;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class QueryStatisticItem {
    private String jobId;
    private String queryExecutionId;
    private String status;
    private Long dataScannedInBytes;
    private Long executionTimeMillis;
    private Long engineExecutionTimeMillis;
    private Long queryQueueTimeMillis;
    private Timestamp createdAt;
    private Timestamp completedAt;
  }
}

