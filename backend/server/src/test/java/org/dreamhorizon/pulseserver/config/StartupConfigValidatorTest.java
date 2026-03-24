package org.dreamhorizon.pulseserver.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
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

  @Mock
  private EmrServerlessConfig emrServerlessConfig;

  /** Valid EMR settings so production tests can isolate ClickHouse failures. */
  private void givenProdValidEmrServerless() {
    when(emrServerlessConfig.isEnabled()).thenReturn(true);
    when(emrServerlessConfig.getRegion()).thenReturn("ap-south-1");
    when(emrServerlessConfig.getApplicationId()).thenReturn("00abc123");
    when(emrServerlessConfig.getJobRoleArn()).thenReturn("arn:aws:iam::111111111111:role/job");
    when(emrServerlessConfig.getExecutionRoleArn()).thenReturn("arn:aws:iam::111111111111:role/exec");
  }

  @Nested
  class ValidateEnvironment {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "stag", "uat", "prod", "DEV", "STAG", "UAT", "PROD", "Dev", "Stag"})
    void validValues_returnsEmpty(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEnvironment();

      assertThat(errors).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"development", "production", "staging", "test", "local", "invalid"})
    void invalidValue_returnsError(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("invalid");
    }

    @Test
    void nullValue_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn(null);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("required");
    }

    @Test
    void blankValue_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("   ");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEnvironment();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("APP_ENVIRONMENT").contains("required");
    }

    @Test
    void emptyValue_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEnvironment();

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

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateClickhouseConfig();

      assertThat(errors).isEmpty();
    }

    @Test
    void prodWithBlankClusterName_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateClickhouseConfig();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("CLICKHOUSE_CLUSTER_NAME").contains("production");
    }

    @Test
    void prodWithNullClusterName_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn(null);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateClickhouseConfig();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("CLICKHOUSE_CLUSTER_NAME").contains("production");
    }

    @Test
    void prodUppercaseWithBlankClusterName_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("PROD");
      when(clickhouseConfig.getClusterName()).thenReturn("  ");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateClickhouseConfig();

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("CLICKHOUSE_CLUSTER_NAME");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "stag", "uat"})
    void nonProdWithClusterName_returnsEmpty(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateClickhouseConfig();

      assertThat(errors).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "stag", "uat"})
    void nonProdWithBlankClusterName_returnsEmpty(String env) {
      when(appConfig.getAppEnvironment()).thenReturn(env);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateClickhouseConfig();

      assertThat(errors).isEmpty();
    }
  }

  @Nested
  class ValidateEmrServerlessConfig {

    @Test
    void nonProd_returnsEmpty() {
      when(appConfig.getAppEnvironment()).thenReturn("dev");
      when(emrServerlessConfig.isEnabled()).thenReturn(false);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      assertThat(validator.validateEmrServerlessConfig()).isEmpty();
    }

    @Test
    void prodWhenFullyConfigured_returnsEmpty() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      givenProdValidEmrServerless();

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      assertThat(validator.validateEmrServerlessConfig()).isEmpty();
    }

    @Test
    void prodWhenDisabled_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(emrServerlessConfig.isEnabled()).thenReturn(false);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEmrServerlessConfig();

      assertThat(errors).anyMatch(msg -> msg.contains("EMR_SERVERLESS_ENABLED"));
    }

    @Test
    void prodWhenRegionBlank_returnsError() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(emrServerlessConfig.isEnabled()).thenReturn(true);
      when(emrServerlessConfig.getRegion()).thenReturn("");
      when(emrServerlessConfig.getApplicationId()).thenReturn("id");
      when(emrServerlessConfig.getJobRoleArn()).thenReturn("arn:aws:iam::1:role/job");
      when(emrServerlessConfig.getExecutionRoleArn()).thenReturn("arn:aws:iam::1:role/exec");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      List<String> errors = validator.validateEmrServerlessConfig();

      assertThat(errors).anyMatch(msg -> msg.contains("EMR_SERVERLESS_REGION"));
    }
  }

  @Nested
  class FullValidation {

    @Test
    void validConfig_noException() {
      when(appConfig.getAppEnvironment()).thenReturn("dev");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      validator.validate();

      assertThat(validator).isNotNull();
    }

    @Test
    void invalidEnvironment_throwsException() {
      when(appConfig.getAppEnvironment()).thenReturn("invalid");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);

      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APP_ENVIRONMENT")
          .hasMessageContaining("invalid");
    }

    @Test
    void prodWithoutClusterName_throwsException() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("");
      givenProdValidEmrServerless();

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);

      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CLICKHOUSE_CLUSTER_NAME")
          .hasMessageContaining("production");
    }

    @Test
    void prodWithValidClickhouseButEmrDisabled_throwsException() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("pulse-clickhouse");
      when(emrServerlessConfig.isEnabled()).thenReturn(false);

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);

      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("EMR_SERVERLESS_ENABLED");
    }

    @Test
    void prodWithValidClickhouseAndEmr_passes() {
      when(appConfig.getAppEnvironment()).thenReturn("prod");
      when(clickhouseConfig.getClusterName()).thenReturn("pulse-clickhouse");
      givenProdValidEmrServerless();

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);
      validator.validate();
    }

    @Test
    void multipleErrors_allReported() {
      when(appConfig.getAppEnvironment()).thenReturn("invalid-env");

      StartupConfigValidator validator =
          new StartupConfigValidator(appConfig, clickhouseConfig, emrServerlessConfig);

      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APP_ENVIRONMENT");
    }

    @Test
    void staticValidateMethod_throwsOnInvalidConfig() {
      when(appConfig.getAppEnvironment()).thenReturn("invalid");

      assertThatThrownBy(
              () -> StartupConfigValidator.validate(appConfig, clickhouseConfig, emrServerlessConfig))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APP_ENVIRONMENT");
    }

    @Test
    void staticValidateMethod_passesOnValidConfig() {
      when(appConfig.getAppEnvironment()).thenReturn("dev");

      StartupConfigValidator.validate(appConfig, clickhouseConfig, emrServerlessConfig);
    }
  }
}
