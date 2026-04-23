package org.dreamhorizon.pulsealertscron.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

  @Test
  void testApplicationConfigCreation() {
    ApplicationConfig config = new ApplicationConfig();
    assertNotNull(config, "ApplicationConfig should be created");
  }

  @Test
  void testPulseServerUrlSetterGetter() {
    ApplicationConfig config = new ApplicationConfig();
    String testUrl = "http://localhost:8080";
    config.setPulseServerUrl(testUrl);
    assertEquals(testUrl, config.getPulseServerUrl(), "Pulse server URL should match");
  }

  @Test
  void shouldUseDefaultSyncIntervalsWhenUnset() {
    ApplicationConfig config = new ApplicationConfig();
    assertEquals(ApplicationConfig.DEFAULT_USAGE_CREDITS_SYNC_INTERVAL_SECONDS,
        config.resolveUsageCreditsSyncIntervalSeconds());
    assertEquals(ApplicationConfig.DEFAULT_API_KEYS_SYNC_INTERVAL_SECONDS,
        config.resolveApiKeysSyncIntervalSeconds());
  }

  @Test
  void shouldClampSyncIntervalsToMinimum() {
    ApplicationConfig config = new ApplicationConfig();
    config.setUsageCreditsSyncIntervalSeconds(1);
    config.setApiKeysSyncIntervalSeconds(2);
    assertEquals(ApplicationConfig.MIN_SYNC_INTERVAL_SECONDS,
        config.resolveUsageCreditsSyncIntervalSeconds());
    assertEquals(ApplicationConfig.MIN_SYNC_INTERVAL_SECONDS,
        config.resolveApiKeysSyncIntervalSeconds());
  }
}

