package org.dreamhorizon.pulseserver.service.analytics;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;

/**
 * Shared SQL helper utilities for funnel and journey ClickHouse compute builders.
 *
 * <p>Source table is {@code otel.otel_logs}. Group key expressions use map access, not
 * materialized column names.
 */
public final class ClickhouseAnalyticsQueryUtils {

  private static final DateTimeFormatter CH_DATETIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private ClickhouseAnalyticsQueryUtils() {}

  /**
   * Returns the ClickHouse SQL expression for the group key column based on analysis mode.
   *
   * @param mode "UNIQUE_USERS" or "SESSIONS" (case-insensitive)
   * @return {@code LogAttributes['user.id']} or {@code LogAttributes['session.id']}
   */
  public static String resolveGroupKey(String mode) {
    if ("SESSIONS".equalsIgnoreCase(mode)) {
      return "LogAttributes['session.id']";
    }
    return "LogAttributes['user.id']";
  }

  /**
   * Returns the ClickHouse SQL expression for the time range start.
   *
   * @param funnelType    funnel schedule: {@code AUTO} or {@code ONCE} (not UNIQUE_USERS / SESSIONS)
   * @param dateRangeDays days to look back (used in AUTO mode)
   * @param startTime     explicit start time (used in ONCE mode; may be null for AUTO)
   */
  public static String resolveStartExpr(String funnelType, int dateRangeDays, java.time.Instant startTime) {
    if ("ONCE".equalsIgnoreCase(funnelType) && startTime != null) {
      LocalDateTime ldt = LocalDateTime.ofInstant(startTime, ZoneOffset.UTC);
      return "toDateTime64('" + ldt.format(CH_DATETIME) + "', 9)";
    }
    return "now() - INTERVAL " + dateRangeDays + " DAY";
  }

  /**
   * Returns the ClickHouse SQL expression for the time range end.
   *
   * @param funnelType funnel schedule: {@code AUTO} or {@code ONCE}
   * @param endTime    explicit end time (used in ONCE mode; may be null for AUTO)
   */
  public static String resolveEndExpr(String funnelType, java.time.Instant endTime) {
    if ("ONCE".equalsIgnoreCase(funnelType) && endTime != null) {
      LocalDateTime ldt = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC);
      return "toDateTime64('" + ldt.format(CH_DATETIME) + "', 9)";
    }
    return "now()";
  }
}
