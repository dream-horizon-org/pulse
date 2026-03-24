package org.dreamhorizon.pulseserver.config;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for AWS EMR Serverless job submission from pulse-server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmrServerlessConfig {

  private static final String DEFAULT_REGION = "ap-south-1";

  private boolean enabled;
  /** Set from config; may be null/blank when {@link #enabled} is false. */
  private String region;
  private String applicationId;
  private String jobRoleArn;
  /** Required when {@link #enabled}; passed to {@code StartJobRun}. */
  private String executionRoleArn;

  public String getEffectiveRegion() {
    return region != null && !region.isBlank() ? region : DEFAULT_REGION;
  }

  /**
   * Builds config from Vert.x merged JSON (see {@code conf/emr-serverless-default.conf}).
   *
   * @throws IllegalStateException if {@code enabled} is true but required fields are missing
   */
  public static EmrServerlessConfig fromJsonObject(JsonObject emr) {
    if (emr == null) {
      emr = new JsonObject();
    }
    boolean enabled = false;
    Object enabledVal = emr.getValue("enabled");
    if (enabledVal instanceof Boolean) {
      enabled = (Boolean) enabledVal;
    } else if (enabledVal instanceof String) {
      enabled = Boolean.parseBoolean((String) enabledVal);
    }
    String region = emr.getString("region");
    String applicationId = emr.getString("applicationId");
    String jobRoleArn = emr.getString("jobRoleArn");
    String executionRoleArn = emr.getString("executionRoleArn");
    EmrServerlessConfig cfg = EmrServerlessConfig.builder()
        .enabled(enabled)
        .region(region)
        .applicationId(applicationId)
        .jobRoleArn(jobRoleArn)
        .executionRoleArn(executionRoleArn)
        .build();
    if (enabled) {
      if (applicationId == null || applicationId.isBlank()) {
        throw new IllegalStateException(
            "emrServerless.enabled is true but applicationId is not set "
                + "(set CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_APPLICATION_ID)");
      }
      if (jobRoleArn == null || jobRoleArn.isBlank()) {
        throw new IllegalStateException(
            "emrServerless.enabled is true but jobRoleArn is not set "
                + "(set CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_JOB_ROLE_ARN)");
      }
      if (region == null || region.isBlank()) {
        throw new IllegalStateException(
            "emrServerless.enabled is true but region is not set "
                + "(set CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_REGION)");
      }
      if (executionRoleArn == null || executionRoleArn.isBlank()) {
        throw new IllegalStateException(
            "emrServerless.enabled is true but executionRoleArn is not set "
                + "(set CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_EXECUTION_ROLE_ARN)");
      }
    }
    return cfg;
  }
}
