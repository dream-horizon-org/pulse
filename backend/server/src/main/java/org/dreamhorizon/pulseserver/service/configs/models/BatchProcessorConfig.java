package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for batch export settings for spans and logs.
 * Defines batch size and schedule delay for BatchSpanProcessor and BatchLogRecordProcessor.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatchProcessorConfig {

  @JsonProperty("batchLogs")
  private BatchProcessorOption batchLogs;

  @JsonProperty("batchSpans")
  private BatchProcessorOption batchSpans;

  /**
   * Individual batch processor option with batch size and schedule delay.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class BatchProcessorOption {

    @JsonProperty("maxExportBatchSize")
    private int maxExportBatchSize;

    @JsonProperty("scheduleDelay")
    private int scheduleDelay; // milliseconds
  }
}
