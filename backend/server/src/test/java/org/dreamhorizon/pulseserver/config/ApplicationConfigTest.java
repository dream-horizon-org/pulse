package org.dreamhorizon.pulseserver.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    assertEquals("interactionPath", config.getInteractionDetailsS3BucketFilePath());
    assertEquals("interactionAsset", config.getInteractionDetailCloudFrontAssetPath());
  }

  @Test
  void allArgsConstructor() {
    ApplicationConfig config = new ApplicationConfig(
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
        "tncS3Bucket"
    );
    assertNotNull(config);
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
