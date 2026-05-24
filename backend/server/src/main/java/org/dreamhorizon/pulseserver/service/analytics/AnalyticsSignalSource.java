package org.dreamhorizon.pulseserver.service.analytics;

import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.AnalysisBasis;

/**
 * ClickHouse table + columns for funnel/journey step identity.
 *
 * <p>EVENT: {@code otel_logs} / {@code custom_event} / {@code EventName}.
 * SCREEN: {@code otel_traces} / {@code screen_load} / {@code ScreenName}.
 */
public final class AnalyticsSignalSource {

  private final String table;
  private final String pulseType;
  private final String stepColumn;

  private AnalyticsSignalSource(String table, String pulseType, String stepColumn) {
    this.table = table;
    this.pulseType = pulseType;
    this.stepColumn = stepColumn;
  }

  public static AnalyticsSignalSource forBasis(AnalysisBasis basis) {
    return basis == AnalysisBasis.SCREEN
      ? traces("screen_load", "ScreenName")
      : logs("custom_event", "EventName");
  }

  public static AnalyticsSignalSource logs(String pulseType, String stepColumn) {
    return new AnalyticsSignalSource("otel.otel_logs", pulseType, stepColumn);
  }

  public static AnalyticsSignalSource traces(String pulseType, String stepColumn) {
    return new AnalyticsSignalSource("otel.otel_traces", pulseType, stepColumn);
  }

  public String getTable() {
    return table;
  }

  public String getPulseType() {
    return pulseType;
  }

  public String getStepColumn() {
    return stepColumn;
  }

  public boolean isScreen() {
    return "ScreenName".equals(stepColumn);
  }

  /** Alias step column as {@code EventName} so downstream funnel SQL stays unchanged. */
  public String stepSelectExpr() {
    return stepColumn + " AS EventName";
  }

  public String stepEquals(String stepName) {
    return stepColumn + " = '" + stepName + "'";
  }

  public String nonEmptyStepFilter() {
    return isScreen() ? "\n      AND ScreenName != ''" : "";
  }
}
