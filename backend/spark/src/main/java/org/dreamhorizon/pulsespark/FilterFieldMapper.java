package org.dreamhorizon.pulsespark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
