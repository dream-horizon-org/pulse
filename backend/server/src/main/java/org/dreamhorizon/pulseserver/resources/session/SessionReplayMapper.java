package org.dreamhorizon.pulseserver.resources.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotBlobResponse;
import org.dreamhorizon.pulseserver.service.session.models.SnapshotEvent;


@Slf4j
public class SessionReplayMapper {

  private final ObjectMapper objectMapper;

  @Inject
  public SessionReplayMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Parses JSONL bytes (one JSON object per line with timestamp, type, data) into
   * SnapshotBlobResponse. The format is same as sent by capture service in snapshot_data.
   */
  public SnapshotBlobResponse jsonlToSnapshotBlobResponse(byte[] jsonlBytes) {
    List<SnapshotEvent> snapshots = new ArrayList<>();
    String text = new String(jsonlBytes, StandardCharsets.UTF_8);
    for (String line : text.split("\n")) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      try {
        Map<String, Object> map = objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {});
        Object ts = map.get("timestamp");
        Object t = map.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) map.get("data");
        long timestamp = ts instanceof Number ? ((Number) ts).longValue() : Long.parseLong(String.valueOf(ts));
        int type = t instanceof Number ? ((Number) t).intValue() : Integer.parseInt(String.valueOf(t));
        snapshots.add(SnapshotEvent.builder()
            .timestamp(timestamp)
            .type(type)
            .data(data != null ? data : Map.of())
            .build());
      } catch (Exception e) {
        log.warn("Skip invalid JSONL line: {}", line.length() > 100 ? line.substring(0, 100) + "..." : line, e);
      }
    }
    return SnapshotBlobResponse.builder().snapshots(snapshots).build();
  }
}
