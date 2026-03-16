package org.dreamhorizon.pulseserver.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class StartupConfigValidator {

  private static final Set<String> VALID_ENVIRONMENTS = Set.of("dev", "stag", "uat", "prod");

  private final ApplicationConfig appConfig;
  private final ClickhouseConfig clickhouseConfig;

  @Inject
  public StartupConfigValidator(
      ApplicationConfig appConfig, ClickhouseConfig clickhouseConfig) {
    this.appConfig = appConfig;
    this.clickhouseConfig = clickhouseConfig;
    validate();
  }

  private void validate() {
    List<String> errors = new ArrayList<>();
    errors.addAll(validateEnvironment());
    errors.addAll(validateClickhouseConfig());
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
