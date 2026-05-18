package org.dreamhorizon.pulseserver.errorgrouping.archive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;

public final class RecordGroupingByProject {

  private RecordGroupingByProject() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Map<String, List<GenericRecord>> partitionByProjectId(List<GenericRecord> rows) {
    Map<String, List<GenericRecord>> by = new HashMap<>();
    for (GenericRecord r : rows) {
      Object pid = r.get("ProjectId");
      String raw = pid == null ? "" : pid.toString();
      String key = raw.isBlank() ? "" : raw;
      by.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
    }
    return by;
  }
}
