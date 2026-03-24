package org.dreamhorizon.pulseserver.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class EmrServerlessConfigTest {

  @Test
  void fromJsonObject_disabled_allowsMissingIds() {
    JsonObject json = new JsonObject().put("enabled", false);
    EmrServerlessConfig cfg = EmrServerlessConfig.fromJsonObject(json);
    assertFalse(cfg.isEnabled());
    assertEquals("ap-south-1", cfg.getEffectiveRegion());
  }

  @Test
  void fromJsonObject_enabledString_requiresApplicationId() {
    JsonObject json = new JsonObject()
        .put("enabled", "true")
        .put("jobRoleArn", "arn:aws:iam::123456789012:role/job");
    assertThrows(IllegalStateException.class, () -> EmrServerlessConfig.fromJsonObject(json));
  }

  @Test
  void fromJsonObject_enabled_requiresJobRole() {
    JsonObject json = new JsonObject()
        .put("enabled", true)
        .put("applicationId", "00abc123def456789");
    assertThrows(IllegalStateException.class, () -> EmrServerlessConfig.fromJsonObject(json));
  }

  @Test
  void fromJsonObject_enabled_ok() {
    JsonObject json = new JsonObject()
        .put("enabled", true)
        .put("applicationId", "00abc123def456789")
        .put("jobRoleArn", "arn:aws:iam::123456789012:role/job")
        .put("region", "ap-south-1");
    EmrServerlessConfig cfg = EmrServerlessConfig.fromJsonObject(json);
    assertTrue(cfg.isEnabled());
    assertEquals("00abc123def456789", cfg.getApplicationId());
    assertEquals("arn:aws:iam::123456789012:role/job", cfg.getJobRoleArn());
  }
}
