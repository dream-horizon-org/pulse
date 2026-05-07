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
   * When {@code bucket} is set, heatmap screenshot list/presign uses this bucket; otherwise session
   * replay S3 bucket. Endpoint/keys fall back to session replay when omitted (same MinIO). Optional
   * {@code presignEndpoint} sets browser-facing hosts in presigned URLs only (see {@code
   * HEATMAP_S3_PRESIGN_ENDPOINT}).
   */
  public HeatmapS3Config heatmapS3;

  /** Redis host for Kong plugin materialization (API key map, usage credits in Part B). */
  public String redisHost;
  /** Redis port for Kong plugin materialization. */
  public Integer redisPort;

  /**
   * Get the dev mode API key with a sensible default.
   * This key is used when GOOGLE_OAUTH_ENABLED=false.
   */
  public String getDevModeApiKey() {
    return devModeApiKey != null && !devModeApiKey.isBlank() 
        ? devModeApiKey 
        : "default-project_devkey01";
  }

  /**
   * Per-project URL for interaction mapping JSON. Normalizes a trailing slash on
   * {@code interactionConfigUrl} so values like {@code http://host/v1/interaction-configs/} do not
   * produce a double slash before {@code /projects/...}.
   *
   * @return {@code null} when the base URL is missing or blank (avoids the literal
   *     {@code "null/projects/..."} that {@link String#format} would otherwise produce)
   */
  public String buildInteractionConfigFileUrl(String projectId) {
    if (interactionConfigUrl == null || interactionConfigUrl.isBlank()) {
      return null;
    }
    String base = stripTrailingSlashes(interactionConfigUrl);
    return String.format("%s/projects/%s/interaction-config.json", base, projectId);
  }

  private static String stripTrailingSlashes(String url) {
    if (url == null || url.isEmpty()) {
      return url;
    }
    int end = url.length();
    while (end > 0 && url.charAt(end - 1) == '/') {
      end--;
    }
    return url.substring(0, end);
  }
}
