package org.dreamhorizon.pulsespark;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.spark.sql.Column;

import static org.apache.spark.sql.functions.element_at;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.not;
import static org.apache.spark.sql.functions.trim;

/**
 * OTL log attribute filters against {@linkplain SparkConstants.OtelLogColumn#LOG_ATTRIBUTES parquet
 * maps} ({@code map&lt;string,string&gt;} in the shared OTL Avro/schema contract).
 *
 * <p>Keys match map entries exactly ({@code "pulse.type"}, {@code "sportName"}, etc.). Missing keys
 * fail {@code EQ}/{@code IN} and succeed {@code NE}/{@code NOT_IN}.
 */
public final class LogAttributesPredicates {

  private LogAttributesPredicates() {}

  private static Column mapColumn(String sqlColumnBareName) {
    return col(sqlColumnBareName);
  }

  /** Value stored under {@code key}, or SQL NULL when the map or key is absent. */
  private static Column valueAt(Column mapCol, String key) {
    return element_at(mapCol, lit(key == null ? "" : key.trim()));
  }

  /**
   * @param sqlColumnBareName logical column ({@link SparkConstants.OtelLogColumn#LOG_ATTRIBUTES})
   * @param attributeKey      map key (OTL attribute id)
   */
  public static Column predicate(String sqlColumnBareName, String attributeKey, String operator,
      java.util.Collection<?> rawValues) {
    String norm = FunnelFilterOperators.normalize(operator == null ? "EQ" : operator);
    List<String> values = rawValues.stream()
        .map(v -> v == null ? "" : String.valueOf(v))
        .collect(Collectors.toList());
    if (values.isEmpty()) {
      return lit(true);
    }

    Column mapCol = mapColumn(sqlColumnBareName);
    Column v = valueAt(mapCol, attributeKey);

    switch (norm) {
      case "EQ":
      case "=":
      case "IN":
        return values.stream().map(val -> matchEquals(v, val)).reduce(Column::or).orElse(lit(false));
      case "NE":
      case "!=":
      case "NOT_IN":
        return notInOrMissing(v, values);
      case "CONTAINS":
        return values.stream().map(sub -> containsValue(v, sub)).reduce(Column::or).orElse(lit(false));
      default:
        return lit(true);
    }
  }

  private static Column matchEquals(Column valueCol, String val) {
    return valueCol.isNotNull().and(valueCol.equalTo(lit(val)));
  }

  /** {@code NE}/{@code NOT_IN}: absent key counts as passing (consistent with typical SQL {@code NOT IN}). */
  private static Column notInOrMissing(Column valueCol, List<String> blacklist) {
    Object[] objs = blacklist.toArray();
    return valueCol.isNull().or(not(valueCol.isin(objs)));
  }

  private static Column containsValue(Column valueCol, String substring) {
    if (substring == null) {
      return lit(false);
    }
    return valueCol.isNotNull().and(trim(valueCol).contains(lit(substring)));
  }
}
