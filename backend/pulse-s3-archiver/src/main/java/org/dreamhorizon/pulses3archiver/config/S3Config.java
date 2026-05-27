package org.dreamhorizon.pulses3archiver.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Config {
  /**
   * Single ingestion bucket — all projects' Parquet files land here, segregated by a
   * {@code <project-id>/} key prefix (e.g. {@code s3://pulse-otel-ingestion/projx/otel_traces/...}).
   */
  private String rootBucket = "pulse-otel-ingestion";
  private String region = "ap-south-1";
  private long multipartThresholdBytes = 16777216L;
  private long multipartPartBytes = 16777216L;
  private int maxConcurrency = 32;
}
