package org.dreamhorizon.pulseserver.service.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BlockListingQueryRow {
  @JsonProperty("start_time")
  private String startTime;
  @JsonProperty("block_first_timestamps")
  private String blockFirstTimestamps;
  @JsonProperty("block_last_timestamps")
  private String blockLastTimestamps;
  @JsonProperty("block_urls")
  private String blockUrls;
  @JsonProperty("snapshot_source")
  private String snapshotSource;
}
