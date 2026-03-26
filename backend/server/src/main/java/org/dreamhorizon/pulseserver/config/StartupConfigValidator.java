package org.dreamhorizon.pulseserver.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartupConfigValidator {

  private static final Set<String> VALID_ENVIRONMENTS = Set.of("dev", "stag", "uat", "prod");

  private final ApplicationConfig appConfig;
  private final ClickhouseConfig clickhouseConfig;
  private final EmrServerlessConfig emrServerlessConfig;

  public StartupConfigValidator(
      ApplicationConfig appConfig,
      ClickhouseConfig clickhouseConfig,
      EmrServerlessConfig emrServerlessConfig) {
    this.appConfig = appConfig;
    this.clickhouseConfig = clickhouseConfig;
    this.emrServerlessConfig = emrServerlessConfig;
  }

  public static void validate(
      ApplicationConfig appConfig,
      ClickhouseConfig clickhouseConfig,
      EmrServerlessConfig emrServerlessConfig) {
    new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig).validate();
  }

  public void validate() {
    List<String> errors = new ArrayList<>();
    errors.addAll(validateEnvironment());
    errors.addAll(validateClickhouseConfig());
    errors.addAll(validateEmrServerlessConfig());
    failIfErrors(errors);
  }

  List<String> validateEnvironment() {
    String env = appConfig.getAppEnvironment();
    if (isBlank(env)) {
      return List.of("APP_ENVIRONMENT is required and cannot be blank");
    }
    if (!VALID_ENVIRONMENTS.contains(env.toLowerCase())) {
      return List.of(
          String.format(
              "APP_ENVIRONMENT '%s' is invalid. Must be one of: %s (case-insensitive)",
              env, VALID_ENVIRONMENTS));
    }
    return Collections.emptyList();
  }

  List<String> validateClickhouseConfig() {
    if (isProduction() && isBlank(clickhouseConfig.getClusterName())) {
      return List.of("CLICKHOUSE_CLUSTER_NAME is required in production environment");
    }
    return Collections.emptyList();
  }

  /**
   * In production, EMR Serverless must be enabled and all related settings non-blank.
   */
  List<String> validateEmrServerlessConfig() {
    if (!isProduction()) {
      return Collections.emptyList();
    }
    List<String> errors = new ArrayList<>();
    if (!emrServerlessConfig.isEnabled()) {
      errors.add(
          "CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_ENABLED must be true in production environment");
    }
    if (isBlank(emrServerlessConfig.getRegion())) {
      errors.add(
          "CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_REGION is required in production environment");
    }
    if (isBlank(emrServerlessConfig.getApplicationId())) {
      errors.add(
          "CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_APPLICATION_ID is required in production environment");
    }
    if (isBlank(emrServerlessConfig.getExecutionRoleArn())) {
      errors.add(
          "CONFIG_SERVICE_APPLICATION_EMR_SERVERLESS_EXECUTION_ROLE_ARN is required in production environment");
    }
    return errors;
  }

  private void failIfErrors(List<String> errors) {
    if (!errors.isEmpty()) {
      String errorMessage =
          "Startup configuration validation failed:\n- " + String.join("\n- ", errors);
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    log.info(
        "Startup configuration validation passed (environment: {})",
        appConfig.getAppEnvironment());
  }

  private boolean isProduction() {
    String env = appConfig.getAppEnvironment();
    return env != null && "prod".equalsIgnoreCase(env);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
