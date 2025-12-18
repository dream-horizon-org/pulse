package org.dreamhorizon.pulsealertscron.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ApplicationConfig
 */
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
}

