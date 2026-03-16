package org.dreamhorizon.pulseserver.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartupConfigValidatorTest {

  @Mock
  private ApplicationConfig appConfig;

  @Mock
  private ClickhouseConfig clickhouseConfig;

  @Nested
  class ValidateEnvironment {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "stag", "uat", "prod", "DEV", "STAG", "UAT", "PROD", "Dev", "Stag"})
    void validValues_returnsEmpty(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateEnvironment();

      assertThat(errors).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"development", "production", "staging", "test", "local", "invalid"})
    void invalidValue_returnsError(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("invalid");
    }

    @Test
    void nullValue_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn(null);

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("required");
    }

    @Test
    void blankValue_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("   ");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("required");
    }

    @Test
    void emptyValue_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("required");
    }
  }

  @Nested
  class ValidateClickhouseConfig {

    @Test
    void prodWithClusterName_returnsEmpty() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("pulse-clickhouse");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateClickhouseConfig();

      assertThat(errors).isEmpty();
    }

    @Test
    void prodWithBlankClusterName_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateClickhouseConfig();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("CLICKHOUSE_CLUSTER_NAME").contains("production");
    }

    @Test
    void prodWithNullClusterName_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn(null);

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateClickhouseConfig();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("CLICKHOUSE_CLUSTER_NAME").contains("production");
    }

    @Test
    void prodUppercaseWithBlankClusterName_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("PROD");
      when(clickhouseConfig.getClusterName()).thenReturn("  ");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateClickhouseConfig();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("CLICKHOUSE_CLUSTER_NAME");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "stag", "uat"})
    void nonProdWithClusterName_returnsEmpty(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);
      when(clickhouseConfig.getClusterName()).thenReturn("cluster-name");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateClickhouseConfig();

      assertThat(errors).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "stag", "uat"})
    void nonProdWithBlankClusterName_returnsEmpty(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);
      when(clickhouseConfig.getClusterName()).thenReturn("");

      StartupConfigValidatorTestHelper helper = new StartupConfigValidatorTestHelper(appConfig, clickhouseConfig);
      List<String> errors = helper.validateClickhouseConfig();

      assertThat(errors).isEmpty();
    }
  }

  @Nested
  class FullValidation {

    @Test
    void validConfig_noException() {
      when(appConfig.getAppEnvironment()).thenReturn("dev");
      when(clickhouseConfig.getClusterName()).thenReturn("");

      StartupConfigValidator validator = new StartupConfigValidator(appConfig, clickhouseConfig);

      assertThat(validator).isNotNull();
    }

    @Test
    void invalidEnvironment_throwsException() {
      when(appConfig.getAppEnvironment()).thenReturn("invalid");

      assertThatThrownBy(() -> new StartupConfigValidator(appConfig, clickhouseConfig))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APP_ENVIRONMENT")
          .hasMessageContaining("invalid");
    }

    @Test
    void prodWithoutClusterName_throwsException() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("");

      assertThatThrownBy(() -> new StartupConfigValidator(appConfig, clickhouseConfig))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CLICKHOUSE_CLUSTER_NAME")
          .hasMessageContaining("production");
    }

    @Test
    void multipleErrors_allReported() {
      when(appConfig.getAppEnvironment()).thenReturn("invalid-env");

      assertThatThrownBy(() -> new StartupConfigValidator(appConfig, clickhouseConfig))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APP_ENVIRONMENT");
    }
  }

  /**
   * Test helper class that exposes package-private validation methods for testing.
   */
  static class StartupConfigValidatorTestHelper {
    private final ApplicationConfig appConfig;
    private final ClickhouseConfig clickhouseConfig;

    StartupConfigValidatorTestHelper(ApplicationConfig appConfig, ClickhouseConfig clickhouseConfig) {
      this.appConfig = appConfig;
      this.clickhouseConfig = clickhouseConfig;
    }

    List<String> validateEnvironment() {
      String env = appConfig.getAppEnvironment();
      if (isBlank(env)) {
        return List.of("APP_ENVIRONMENT is required and cannot be blank");
      }
      if (!java.util.Set.of("dev", "stag", "uat", "prod").contains(env.toLowerCase())) {
        return List.of(
            String.format(
                "APP_ENVIRONMENT '%s' is invalid. Must be one of: [dev, stag, uat, prod] (case-insensitive)",
                env));
      }
      return java.util.Collections.emptyList();
    }

    List<String> validateClickhouseConfig() {
      if (isProduction() && isBlank(clickhouseConfig.getClusterName())) {
        return List.of("CLICKHOUSE_CLUSTER_NAME is required in production environment");
      }
      return java.util.Collections.emptyList();
    }

    private boolean isProduction() {
      String env = appConfig.getAppEnvironment();
      return env != null && "prod".equalsIgnoreCase(env);
    }

    private boolean isBlank(String value) {
      return value == null || value.isBlank();
    }
  }
}
