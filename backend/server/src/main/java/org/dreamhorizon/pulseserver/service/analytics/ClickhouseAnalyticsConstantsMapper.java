package org.dreamhorizon.pulseserver.service.analytics;

import java.util.List;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelFilterOperator;

/**
 * Maps {@link FunnelAttributeFilter} field keys and operators to ClickHouse SQL fragments.
 *
 * <p>Only {@code OS_NAME}, {@code OS_VERSION}, and {@code APP_BUILD_NAME} are supported; each maps
 * to a materialized column on {@code otel.otel_logs} ({@code Platform}, {@code OsVersion},
 * {@code AppVersion}).
 */
public final class ClickhouseAnalyticsConstantsMapper {

  private ClickhouseAnalyticsConstantsMapper() {}

  public static String toSqlClause(FunnelAttributeFilter filter) {
    String col = toColumnExpression(filter.getField());
    String op = toOperatorSql(filter.getOperator(), filter.getValue());
    return "AND " + col + " " + op;
  }

  private static String toColumnExpression(String field) {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("Funnel/journey filter field is required");
    }
    return switch (field.toUpperCase()) {
      case "OS_NAME"        -> "Platform";
      case "OS_VERSION"     -> "OsVersion";
      case "APP_BUILD_NAME" -> "AppVersion";
      default ->
          throw new IllegalArgumentException(
              "Unsupported filter field for analytics: " + field
                  + " (allowed: OS_NAME, OS_VERSION, APP_BUILD_NAME)");
    };
  }

  private static String toOperatorSql(FunnelFilterOperator operator, List<Object> values) {
    if (operator == null || values == null || values.isEmpty()) {
      return "= ''";
    }
    return switch (operator) {
      case EQ      -> "= '" + escape(values.get(0)) + "'";
      case NE      -> "!= '" + escape(values.get(0)) + "'";
      case IN      -> "IN (" + toQuotedList(values) + ")";
      case NOT_IN  -> "NOT IN (" + toQuotedList(values) + ")";
    };
  }

  private static String toQuotedList(List<Object> values) {
    return values.stream()
        .map(v -> "'" + escape(v) + "'")
        .collect(Collectors.joining(", "));
  }

  private static String escape(Object value) {
    if (value == null) {
      return "";
    }
    return value.toString().replace("'", "\\'");
  }
}
