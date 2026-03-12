package org.dreamhorizon.pulseserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigClassesTest {

  @Nested
  class NotificationConfigTest {

    @Test
    void shouldHaveGettersAndSetters() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.AwsConfig aws = new NotificationConfig.AwsConfig("us-west-2");
      NotificationConfig.SqsConfig sqs = new NotificationConfig.SqsConfig();
      sqs.setQueueUrl("https://sqs.amazonaws.com/123/queue");
      sqs.setDlqUrl("https://sqs.amazonaws.com/123/dlq");
      sqs.setVisibilityTimeoutSeconds(120);
      sqs.setWaitTimeSeconds(10);
      sqs.setMaxReceiveCount(5);

      config.setAws(aws);
      config.setSqs(sqs);

      assertThat(config.getAws()).isEqualTo(aws);
      assertThat(config.getSqs()).isEqualTo(sqs);
      assertThat(config.getRegion()).isEqualTo("us-west-2");
    }

    @Test
    void shouldReturnDefaultRegionWhenAwsNull() {
      NotificationConfig config = new NotificationConfig();

      assertThat(config.getRegion()).isEqualTo("ap-south-1");
    }

    @Test
    void shouldReturnDefaultRegionWhenAwsRegionNull() {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig(null));

      assertThat(config.getRegion()).isEqualTo("ap-south-1");
    }

    @Test
    void shouldReturnSqsEnabledWhenQueueUrlSet() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.SqsConfig sqs = new NotificationConfig.SqsConfig();
      sqs.setQueueUrl("https://sqs.amazonaws.com/queue");
      config.setSqs(sqs);

      assertThat(config.isSqsEnabled()).isTrue();
    }

    @Test
    void shouldReturnSqsDisabledWhenQueueUrlNull() {
      NotificationConfig config = new NotificationConfig();
      config.setSqs(new NotificationConfig.SqsConfig());

      assertThat(config.isSqsEnabled()).isFalse();
    }

    @Test
    void shouldReturnDlqEnabledWhenDlqUrlSet() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.SqsConfig sqs = new NotificationConfig.SqsConfig();
      sqs.setDlqUrl("https://sqs.amazonaws.com/dlq");
      config.setSqs(sqs);

      assertThat(config.isDlqEnabled()).isTrue();
    }

    @Test
    void shouldReturnDefaultRetryConfigWhenNull() {
      NotificationConfig config = new NotificationConfig();

      NotificationConfig.RetryConfig retry = config.getRetryConfig();

      assertThat(retry).isNotNull();
      assertThat(retry.getMaxAttempts()).isEqualTo(3);
      assertThat(retry.getInitialDelayMs()).isEqualTo(1000L);
    }

    @Test
    void shouldReturnDefaultWorkerConfigWhenNull() {
      NotificationConfig config = new NotificationConfig();

      NotificationConfig.WorkerConfig worker = config.getWorkerConfig();

      assertThat(worker).isNotNull();
      assertThat(worker.getBatchSize()).isEqualTo(10);
      assertThat(worker.isEnabled()).isTrue();
    }

    @Test
    void shouldReturnDefaultSlackOAuthConfigWhenNull() {
      NotificationConfig config = new NotificationConfig();

      NotificationConfig.SlackOAuthConfig slack = config.getSlackOAuthConfig();

      assertThat(slack).isNotNull();
      assertThat(slack.getScopes()).isEqualTo("chat:write,chat:write.public,channels:read");
    }

    @Test
    void shouldHaveSqsConfigIsEnabled() {
      NotificationConfig.SqsConfig sqs = new NotificationConfig.SqsConfig();
      sqs.setQueueUrl("  https://queue  ");
      assertThat(sqs.isEnabled()).isTrue();

      sqs.setQueueUrl("");
      assertThat(sqs.isEnabled()).isFalse();

      sqs.setQueueUrl(null);
      assertThat(sqs.isEnabled()).isFalse();
    }

    @Test
    void shouldHaveSqsConfigIsDlqEnabled() {
      NotificationConfig.SqsConfig sqs = new NotificationConfig.SqsConfig();
      sqs.setDlqUrl("https://dlq");
      assertThat(sqs.isDlqEnabled()).isTrue();

      sqs.setDlqUrl(null);
      assertThat(sqs.isDlqEnabled()).isFalse();
    }

    @Test
    void shouldHaveSlackOAuthConfigIsEnabled() {
      NotificationConfig.SlackOAuthConfig oauth = new NotificationConfig.SlackOAuthConfig();
      oauth.setClientId("client");
      oauth.setClientSecret("secret");
      assertThat(oauth.isEnabled()).isTrue();

      oauth.setClientId("");
      assertThat(oauth.isEnabled()).isFalse();
    }

    @Test
    void shouldSupportAllArgsConstructor() {
      NotificationConfig.AwsConfig aws = new NotificationConfig.AwsConfig("eu-west-1");
      NotificationConfig.SqsConfig sqs = new NotificationConfig.SqsConfig(
          "queue", "dlq", 300, 20, 5);
      NotificationConfig.RetryConfig retry = new NotificationConfig.RetryConfig(5, 2000L, 60000L, 3.0);
      NotificationConfig.WorkerConfig worker = new NotificationConfig.WorkerConfig(20, 90, 2, false);

      assertThat(aws.getRegion()).isEqualTo("eu-west-1");
      assertThat(sqs.getQueueUrl()).isEqualTo("queue");
      assertThat(sqs.getMaxReceiveCount()).isEqualTo(5);
      assertThat(retry.getMaxAttempts()).isEqualTo(5);
      assertThat(worker.getBatchSize()).isEqualTo(20);
      assertThat(worker.isEnabled()).isFalse();
    }
  }

  @Nested
  class ClickhouseConfigTest {

    @Test
    void shouldHaveAllGettersAndSetters() {
      ClickhouseConfig config = new ClickhouseConfig();
      config.setR2dbcUrl("r2dbc:clickhouse://localhost:8123/default");
      config.setUsername("user");
      config.setPassword("pass");
      config.setInitsize(5);
      config.setMaxsize(20);
      config.setHost("clickhouse.example.com");
      config.setPort(8123);

      assertThat(config.getR2dbcUrl()).isEqualTo("r2dbc:clickhouse://localhost:8123/default");
      assertThat(config.getUsername()).isEqualTo("user");
      assertThat(config.getPassword()).isEqualTo("pass");
      assertThat(config.getInitsize()).isEqualTo(5);
      assertThat(config.getMaxsize()).isEqualTo(20);
      assertThat(config.getHost()).isEqualTo("clickhouse.example.com");
      assertThat(config.getPort()).isEqualTo(8123);
    }

    @Test
    void shouldSupportAllArgsConstructor() {
      ClickhouseConfig config = new ClickhouseConfig(
          "r2dbc:url", "u", "p", 1, 10, "host", 9000);

      assertThat(config.getR2dbcUrl()).isEqualTo("r2dbc:url");
      assertThat(config.getPort()).isEqualTo(9000);
    }
  }

  @Nested
  class OpenFgaConfigTest {

    @Test
    void shouldHaveAllGettersAndSetters() {
      OpenFgaConfig config = OpenFgaConfig.builder()
          .apiUrl("http://localhost:8080")
          .storeId("store-123")
          .authorizationModelId("model-456")
          .enabled(true)
          .build();

      assertThat(config.getApiUrl()).isEqualTo("http://localhost:8080");
      assertThat(config.getStoreId()).isEqualTo("store-123");
      assertThat(config.getAuthorizationModelId()).isEqualTo("model-456");
      assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void shouldSupportBuilderWithAllFields() {
      OpenFgaConfig config = OpenFgaConfig.builder()
          .apiUrl("https://fga.example.com")
          .storeId("s1")
          .authorizationModelId("m1")
          .enabled(false)
          .build();

      assertThat(config.getApiUrl()).isEqualTo("https://fga.example.com");
      assertThat(config.isEnabled()).isFalse();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
      OpenFgaConfig config = new OpenFgaConfig();
      config.setApiUrl("url");
      assertThat(config.getApiUrl()).isEqualTo("url");
    }
  }

  @Nested
  class ApplicationConfigTest {

    @Test
    void shouldHaveAllPublicFields() {
      ApplicationConfig config = new ApplicationConfig();
      config.setCronManagerBaseUrl("http://cron:4000");
      config.setServiceUrl("http://server:8080");
      config.setShutdownGracePeriod(30);
      config.setGoogleOAuthClientId("client-id");
      config.setGoogleOAuthEnabled(true);
      config.setFirebaseProjectId("firebase-proj");
      config.setJwtSecret("secret");
      config.setOtelCollectorUrl("http://otel:4317");
      config.setInteractionConfigUrl("http://config");
      config.setLogsCollectorUrl("http://logs");
      config.setMetricCollectorUrl("http://metrics");
      config.setSpanCollectorUrl("http://spans");
      config.setCustomEventCollectorUrl("http://events");
      config.setS3BucketName("pulse-bucket");
      config.setConfigDetailsS3BucketFilePath("config/path");
      config.setCloudFrontDistributionId("cf-id");
      config.setConfigDetailCloudFrontAssetPath("assets/path");
      config.setWebhookUrl("http://webhook");
      config.setInteractionDetailsS3BucketFilePath("interaction/path");
      config.setInteractionDetailCloudFrontAssetPath("interaction/assets");
      config.setEncryptionMasterKey("master-key");

      assertThat(config.getCronManagerBaseUrl()).isEqualTo("http://cron:4000");
      assertThat(config.getServiceUrl()).isEqualTo("http://server:8080");
      assertThat(config.getShutdownGracePeriod()).isEqualTo(30);
      assertThat(config.getGoogleOAuthClientId()).isEqualTo("client-id");
      assertThat(config.getEncryptionMasterKey()).isEqualTo("master-key");
    }

    @Test
    void shouldSupportAllArgsConstructor() {
      ApplicationConfig config = new ApplicationConfig(
          "cron", "service", 10, "oauth", true, "firebase",
          "jwt", "otel", "config", "logs", "metric", "span", "events",
          "bucket", "configPath", "cfId", "cfPath", "webhook",
          "interPath", "interCfPath", "encKey", "tncBucket");

      assertThat(config.getCronManagerBaseUrl()).isEqualTo("cron");
      assertThat(config.getServiceUrl()).isEqualTo("service");
      assertThat(config.getShutdownGracePeriod()).isEqualTo(10);
      assertThat(config.getEncryptionMasterKey()).isEqualTo("encKey");
    }
  }

  @Nested
  class ConfigUtilsTest {

    @Test
    void shouldCreateConfigRetriever() {
      Vertx vertx = Vertx.vertx();
      try {
        var retriever = ConfigUtils.getConfigRetriever(vertx);
        assertThat(retriever).isNotNull();
      } finally {
        vertx.close();
      }
    }
  }
}
