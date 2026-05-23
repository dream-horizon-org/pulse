package org.dreamhorizon.pulsespark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.spark.sql.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.spark.sql.functions.col;

public final class FilterFieldMapper {

  private static final Logger log = LoggerFactory.getLogger(FilterFieldMapper.class);

  private static final Map<String, String> CATALOG_KEY_TO_COLUMN;

  static {
    var m = new LinkedHashMap<String, String>();
    m.put("OS_NAME", "os_name");
    m.put("APP_BUILD_NAME", "app_build_name");
    m.put("OS_VERSION", "os_version");
    CATALOG_KEY_TO_COLUMN = Collections.unmodifiableMap(m);
  }

  private FilterFieldMapper() {}

  /**
   * Returns a Spark {@link Column} expression for the given filter field.
   * Known catalog keys (OS_NAME, APP_BUILD_NAME, OS_VERSION) resolve to their
   * projected top-level columns. Any other field resolves to a top-level column
   * of the same name — custom event properties are uplifted from {@code log_attributes}
   * before the dataset is cached, so they are always available as direct columns.
   */
  public static Column toColumn(String field) {
    if (field == null || field.isBlank()) {
      return col(field);
    }
    String mapped = CATALOG_KEY_TO_COLUMN.get(field.toUpperCase());
    if (mapped != null) {
      return col(mapped);
    }
    return col(field);
  }

  /** Returns true if {@code field} is a known catalog key that is already a top-level column. */
  public static boolean isKnownCatalogKey(String field) {
    return field != null && CATALOG_KEY_TO_COLUMN.containsKey(field.toUpperCase());
  }

  /** @deprecated Use {@link #toColumn(String)} instead. */
  @Deprecated
  public static String toParquetColumn(String field) {
    if (field == null || field.isBlank()) {
      return field;
    }
    if (CATALOG_KEY_TO_COLUMN.containsKey(field)) {
      return CATALOG_KEY_TO_COLUMN.get(field);
    }
    log.warn("Filter field '{}' not found in mapping; using as column name as-is", field);
    return field;
  }
}
