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
   * Returns the ClickHouse SQL expression for the group key column based on funnel/journey type.
   *
   * @param funnelType "UNIQUE_USERS" or "SESSIONS" (case-insensitive)
   * @return {@code LogAttributes['user.id']} or {@code LogAttributes['session.id']}
   */
  public static String resolveGroupKey(String funnelType) {
    if ("SESSIONS".equalsIgnoreCase(funnelType)) {
      return "LogAttributes['session.id']";
    }
    return "LogAttributes['user.id']";
  }

  /**
   * Returns the ClickHouse SQL expression for the time range start.
   *
   * @param mode          "AUTO" or "ONCE"
   * @param dateRangeDays days to look back (used in AUTO mode)
   * @param startTime     explicit start time (used in ONCE mode; may be null for AUTO)
   */
  public static String resolveStartExpr(String mode, int dateRangeDays, java.time.Instant startTime) {
    if ("ONCE".equalsIgnoreCase(mode) && startTime != null) {
      LocalDateTime ldt = LocalDateTime.ofInstant(startTime, ZoneOffset.UTC);
      return "toDateTime64('" + ldt.format(CH_DATETIME) + "', 9)";
    }
    return "now() - INTERVAL " + dateRangeDays + " DAY";
  }

  /**
   * Returns the ClickHouse SQL expression for the time range end.
   *
   * @param mode    "AUTO" or "ONCE"
   * @param endTime explicit end time (used in ONCE mode; may be null for AUTO)
   */
  public static String resolveEndExpr(String mode, java.time.Instant endTime) {
    if ("ONCE".equalsIgnoreCase(mode) && endTime != null) {
      LocalDateTime ldt = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC);
      return "toDateTime64('" + ldt.format(CH_DATETIME) + "', 9)";
    }
    return "now()";
  }
}
