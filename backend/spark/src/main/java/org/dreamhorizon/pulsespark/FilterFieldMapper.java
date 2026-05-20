package org.dreamhorizon.pulsespark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FilterFieldMapper {

  private static final Logger log = LoggerFactory.getLogger(FilterFieldMapper.class);

  private static final Map<String, String> CATALOG_KEY_TO_COLUMN;

  static {
    var m = new LinkedHashMap<String, String>();
    m.put("OS_NAME", SparkConstants.OtelLogColumn.PLATFORM);
    m.put("APP_BUILD_NAME", SparkConstants.OtelLogColumn.APP_VERSION);
    m.put("OS_VERSION", SparkConstants.OtelLogColumn.OS_VERSION);
    CATALOG_KEY_TO_COLUMN = Collections.unmodifiableMap(m);
  }

  private FilterFieldMapper() {}

  /** True when field maps to a materialized parquet column backed by otel_logs (not solely LogAttributes). */
  public static boolean isCatalogMapped(String field) {
    if (field == null || field.isBlank()) {
      return false;
    }
    return CATALOG_KEY_TO_COLUMN.containsKey(field.trim().toUpperCase(Locale.ROOT));
  }

  public static String toParquetColumn(String field) {
    if (field == null || field.isBlank()) {
      return field;
    }
    String upper = field.trim().toUpperCase(Locale.ROOT);
    if (CATALOG_KEY_TO_COLUMN.containsKey(upper)) {
      return CATALOG_KEY_TO_COLUMN.get(upper);
    }
    log.warn("Filter field '{}' not found in mapping; using as column name as-is", field);
    return field.trim();
  }
}
