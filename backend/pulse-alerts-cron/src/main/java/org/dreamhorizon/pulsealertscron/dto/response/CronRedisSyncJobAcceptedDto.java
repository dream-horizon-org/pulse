package org.dreamhorizon.pulsealertscron.dto.response;

import io.vertx.core.json.JsonObject;
import java.util.Optional;
import lombok.Value;

/** Subset of pulse-server {@code Response.data} for POST .../sync-to-redis (202). */
@Value
public class CronRedisSyncJobAcceptedDto {
  long jobId;
  boolean deduplicated;
  String jobType;

  /**
   * Parses {@code {"data":{"jobId":...}}} from a pulse-server JSON body; empty if missing or invalid.
   */
  public static Optional<CronRedisSyncJobAcceptedDto> tryParse(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return Optional.empty();
    }
    try {
      JsonObject root = new JsonObject(responseBody);
      JsonObject data = root.getJsonObject("data");
      if (data == null) {
        return Optional.empty();
      }
      Long jobId = data.getLong("jobId");
      Boolean deduplicated = data.getBoolean("deduplicated");
      String jobType = data.getString("jobType");
      if (jobId == null || deduplicated == null) {
        return Optional.empty();
      }
      return Optional.of(new CronRedisSyncJobAcceptedDto(
          jobId,
          deduplicated,
          jobType != null ? jobType : ""));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
