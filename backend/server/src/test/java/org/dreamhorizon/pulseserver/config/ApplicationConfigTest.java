package org.dreamhorizon.pulseserver.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

  @Test
  void noArgsConstructorCreatesInstance() {
    ApplicationConfig config = new ApplicationConfig();
    assertNotNull(config);
  }

  @Test
  void allSettersAndGetters() {
    ApplicationConfig config = new ApplicationConfig();
    config.setCronManagerBaseUrl("cronUrl");
    config.setServiceUrl("serviceUrl");
    config.setShutdownGracePeriod(30);
    config.setGoogleOAuthClientId("clientId");
    config.setGoogleOAuthEnabled(true);
    config.setFirebaseProjectId("proj1");
    config.setJwtSecret("secret");
    config.setOtelCollectorUrl("otel");
    config.setInteractionConfigUrl("interactionConfig");
    config.setLogsCollectorUrl("logs");
    config.setMetricCollectorUrl("metric");
    config.setSpanCollectorUrl("span");
    config.setCustomEventCollectorUrl("customEvent");
    config.setS3BucketName("bucket");
    config.setConfigDetailsS3BucketFilePath("configPath");
    config.setCloudFrontDistributionId("cfId");
    config.setConfigDetailCloudFrontAssetPath("cfPath");
    config.setWebhookUrl("webhook");
    config.setTncS3BucketName("tncS3BucketName");
    config.setSymbolFilesS3BucketName("symbolFilesS3BucketName");
    config.setInteractionDetailsS3BucketFilePath("interactionPath");
    config.setInteractionDetailCloudFrontAssetPath("interactionAsset");

    assertEquals("cronUrl", config.getCronManagerBaseUrl());
    assertEquals("serviceUrl", config.getServiceUrl());
    assertEquals(30, config.getShutdownGracePeriod());
    assertEquals("clientId", config.getGoogleOAuthClientId());
    assertEquals(true, config.getGoogleOAuthEnabled());
    assertEquals("proj1", config.getFirebaseProjectId());
    assertEquals("secret", config.getJwtSecret());
    assertEquals("otel", config.getOtelCollectorUrl());
    assertEquals("interactionConfig", config.getInteractionConfigUrl());
    assertEquals("logs", config.getLogsCollectorUrl());
    assertEquals("metric", config.getMetricCollectorUrl());
    assertEquals("span", config.getSpanCollectorUrl());
    assertEquals("customEvent", config.getCustomEventCollectorUrl());
    assertEquals("bucket", config.getS3BucketName());
    assertEquals("configPath", config.getConfigDetailsS3BucketFilePath());
    assertEquals("cfId", config.getCloudFrontDistributionId());
    assertEquals("cfPath", config.getConfigDetailCloudFrontAssetPath());
    assertEquals("webhook", config.getWebhookUrl());
    assertEquals("tncS3BucketName", config.getTncS3BucketName());
    assertEquals("symbolFilesS3BucketName", config.getSymbolFilesS3BucketName());
    assertEquals("interactionPath", config.getInteractionDetailsS3BucketFilePath());
    assertEquals("interactionAsset", config.getInteractionDetailCloudFrontAssetPath());
  }

  @Test
  void allArgsConstructor() {
    ApplicationConfig config = new ApplicationConfig(
        "dev",
        "cronUrl",
        "serviceUrl",
        30,
        "clientId",
        true,
        "proj1",
        "secret",
        "otel",
        "interactionConfig",
        "logs",
        "metric",
        "span",
        "customEvent",
        "bucket",
        "configPath",
        "cfId",
        "cfPath",
        "webhook",
        "interactionPath",
        "interactionAsset",
        "key",
        "tncS3Bucket",
        "http://ai:8000",
        "symbolFilesS3Bucket",
        "dev-api-key",
        new SessionReplayS3Config(
            "session-replay-bucket",
            "http://minio:9000",
            "us-east-1",
            "access-key",
            "secret-key"),
        "replayBaseUrl",
        null,
        null,
        "localhost",
        6379,
        null
    );
    assertNotNull(config);
    assertEquals("dev", config.getAppEnvironment());
    assertEquals("cronUrl", config.getCronManagerBaseUrl());
    assertEquals("proj1", config.getFirebaseProjectId());
    assertEquals("interactionAsset", config.getInteractionDetailCloudFrontAssetPath());
  }

  @Test
  void equalsAndHashCode() {
    ApplicationConfig a = new ApplicationConfig();
    a.setFirebaseProjectId("p1");
    a.setGoogleOAuthClientId("c1");

    ApplicationConfig b = new ApplicationConfig();
    b.setFirebaseProjectId("p1");
    b.setGoogleOAuthClientId("c1");

    ApplicationConfig c = new ApplicationConfig();
    c.setFirebaseProjectId("p2");

    assertTrue(a.equals(a));
    assertTrue(a.equals(b));
    assertTrue(b.equals(a));
    assertFalse(a.equals(c));
    assertFalse(a.equals(null));
    assertFalse(a.equals("not a config"));

    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(0, a.hashCode());
  }

  @Test
  void buildInteractionConfigFileUrlStripsTrailingSlashOnBase() {
    ApplicationConfig config = new ApplicationConfig();
    config.setInteractionConfigUrl("http://10.0.2.2:8080/v1/interaction-configs/");
    assertEquals(
        "http://10.0.2.2:8080/v1/interaction-configs/projects/default-project/interaction.json",
        config.buildInteractionConfigFileUrl("default-project"));
  }

  @Test
  void buildInteractionConfigFileUrlStripsMultipleTrailingSlashes() {
    ApplicationConfig config = new ApplicationConfig();
    config.setInteractionConfigUrl("https://cdn.example.com/base///");
    assertEquals(
        "https://cdn.example.com/base/projects/p1/interaction.json",
        config.buildInteractionConfigFileUrl("p1"));
  }

  @Test
  void buildInteractionConfigFileUrlWorksWithoutTrailingSlashOnBase() {
    ApplicationConfig config = new ApplicationConfig();
    config.setInteractionConfigUrl("https://cdn.example.com");
    assertEquals(
        "https://cdn.example.com/projects/x/interaction.json",
        config.buildInteractionConfigFileUrl("x"));
  }

  @Test
  void buildInteractionConfigFileUrlReturnsNullWhenBaseUrlMissing() {
    ApplicationConfig config = new ApplicationConfig();
    assertNull(config.buildInteractionConfigFileUrl("p1"));
  }

  @Test
  void buildInteractionConfigFileUrlReturnsNullWhenBaseUrlBlank() {
    ApplicationConfig config = new ApplicationConfig();
    config.setInteractionConfigUrl("   ");
    assertNull(config.buildInteractionConfigFileUrl("p1"));
  }

  @Test
  void toStringContainsFields() {
    ApplicationConfig config = new ApplicationConfig();
    config.setFirebaseProjectId("proj1");
    config.setServiceUrl("http://localhost");

    String s = config.toString();
    assertNotNull(s);
    assertTrue(s.contains("ApplicationConfig"));
    assertTrue(s.contains("firebaseProjectId=proj1") || s.contains("proj1"));
    assertTrue(s.contains("serviceUrl=http://localhost") || s.contains("localhost"));
  }
}
