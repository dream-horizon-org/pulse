package org.dreamhorizon.pulseserver.service.analytics;

import java.util.List;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelFilterOperator;

/**
 * Maps {@link FunnelAttributeFilter} field keys and operators to ClickHouse SQL fragments.
 *
 * <p>Source table is {@code otel.otel_logs} — no materialized columns are available for app/device
 * attributes. All lookups use {@code LogAttributes['key']} or {@code ResourceAttributes['key']}
 * map access directly.
 */
public final class ClickhouseAnalyticsConstantsMapper {

  private ClickhouseAnalyticsConstantsMapper() {}

  /**
   * Converts a single {@link FunnelAttributeFilter} to an {@code AND <col> <op> <val>} SQL clause.
   */
  public static String toSqlClause(FunnelAttributeFilter filter) {
    String col = toColumnExpression(filter.getField());
    String op = toOperatorSql(filter.getOperator(), filter.getValue());
    return "AND " + col + " " + op;
  }

  private static String toColumnExpression(String field) {
    if (field == null) {
      return "LogAttributes['']";
    }
    return switch (field.toUpperCase()) {
      case "OS_NAME"             -> "ResourceAttributes['os.name']";
      case "OS_VERSION"          -> "ResourceAttributes['os.version']";
      case "APP_BUILD_NAME"      -> "ResourceAttributes['app.build_name']";
      case "APP_BUILD_ID"        -> "ResourceAttributes['app.build_id']";
      case "DEVICE_MANUFACTURER" -> "ResourceAttributes['device.manufacturer']";
      case "DEVICE_MODEL_ID"     -> "ResourceAttributes['device.model.identifier']";
      case "ANDROID_OS_API_LEVEL"-> "ResourceAttributes['android.os.api_level']";
      case "SCREEN_NAME"         -> "LogAttributes['screen.name']";
      case "PULSE_APP_STATE"     -> "LogAttributes['pulse.app_state']";
      default                    -> "LogAttributes['" + field.toLowerCase() + "']";
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
