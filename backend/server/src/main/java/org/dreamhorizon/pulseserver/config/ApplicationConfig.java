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
  public String cronManagerBaseUrl;
  public String serviceUrl;
  public Integer shutdownGracePeriod;
  public String googleOAuthClientId;
  public Boolean googleOAuthEnabled;
  public String jwtSecret;
  public String otelCollectorUrl;
  public String interactionConfigUrl;
  public String logsCollectorUrl;
  public String metricCollectorUrl;
  public String spanCollectorUrl;
  public String s3BucketName;
  public String configDetailsS3BucketFilePath;
  public String cloudFrontDistributionId;
  public String configDetailCloudFrontAssetPath;
  public String webhookUrl;
  public String interactionDetailsS3BucketFilePath;
  public String interactionDetailCloudFrontAssetPath;
}
