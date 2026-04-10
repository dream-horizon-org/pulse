package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class ApplicationConfig {
  public String appEnvironment;
  public String cronManagerBaseUrl;
  public String serviceUrl;
  public Integer shutdownGracePeriod;
  public String googleOAuthClientId;
  public Boolean googleOAuthEnabled;
  public String firebaseProjectId;
  public String jwtSecret;
  public String otelCollectorUrl;
  public String interactionConfigUrl;
  public String logsCollectorUrl;
  public String metricCollectorUrl;
  public String spanCollectorUrl;
  public String customEventCollectorUrl;
  public String s3BucketName;
  public String configDetailsS3BucketFilePath;
  public String cloudFrontDistributionId;
  public String configDetailCloudFrontAssetPath;
  public String webhookUrl;
  public String interactionDetailsS3BucketFilePath;
  public String interactionDetailCloudFrontAssetPath;
  public String encryptionMasterKey;
  public String tncS3BucketName;
  public String aiServiceUrl;
  public String symbolFilesS3BucketName;
  public String devModeApiKey;
  public SessionReplayS3Config sessionReplayS3;
  public String replayApiBaseUrl;
  /** S3 key prefix for heatmap screenshot JSON (ingestion default: heatmap-screenshots). */
  public String heatmapScreenshotsS3Prefix;
  /**
   * Optional public or CDN base URL for heatmap screenshot objects (no trailing slash). When set,
   * API returns {@code base + "/" + objectKey}. When unset, presigned GET URLs are used.
   */
  public String heatmapScreenshotsPublicBaseUrl;
  /**
   * When {@code bucket} is set, heatmap screenshot list/presign uses this bucket; otherwise session
   * replay S3 bucket. Endpoint/keys fall back to session replay when omitted (same MinIO).
   */
  public HeatmapS3Config heatmapS3;

  /**
   * Get the dev mode API key with a sensible default.
   * This key is used when GOOGLE_OAUTH_ENABLED=false.
   */
  public String getDevModeApiKey() {
    return devModeApiKey != null && !devModeApiKey.isBlank() 
        ? devModeApiKey 
        : "default-project_devkey01";
  }
}
