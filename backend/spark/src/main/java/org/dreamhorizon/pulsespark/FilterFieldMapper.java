package org.dreamhorizon.pulsespark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps event-catalog / API filter keys (e.g. {@code OS_NAME} from {@code event_catalog_entries.FilterKey})
 * to Parquet column names used in funnel/journey Spark jobs ({@link FunnelComputeJob#applyFilters}).
 *
 * <p>When {@link #toParquetColumn(String)} receives a name that is not a known catalog key, it is returned
 * unchanged so callers can use native Parquet column names (e.g. {@code os_name}) directly.
 */
public final class FilterFieldMapper {

  private static final Logger log = LoggerFactory.getLogger(FilterFieldMapper.class);

  /** Catalog {@code FilterKey} → Parquet column (aligned with {@code event_catalog_entries} dimensions). */
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
   * Resolves a filter {@code field} from persisted JSON to the Parquet column name used in
   * {@link FunnelComputeJob#READ_COLS} / {@code buildReadExprs}.
   */
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
