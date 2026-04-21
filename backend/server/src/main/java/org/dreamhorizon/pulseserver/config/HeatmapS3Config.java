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
  /** In-docker S3 API URL for ListObjects (e.g. {@code http://minio:9000}). */
  private String endpoint;
  /**
   * Optional URL host for presigned GETs only (browser must resolve it). When unset, {@link #endpoint}
   * is used. Local: {@code http://localhost:9100} while {@link #endpoint} stays {@code http://minio:9000}.
   * Omit in AWS unless signing must use a different public hostname than listing.
   */
  private String presignEndpoint;
  private String region;
  private String accessKeyId;
  private String secretAccessKey;
}
