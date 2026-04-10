package org.dreamhorizon.pulseserver.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** S3 settings for heatmap screenshot objects (dedicated bucket, e.g. {@code heatmap-assets}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapS3Config {
  private String bucket;
  private String endpoint;
  private String region;
  private String accessKeyId;
  private String secretAccessKey;
}
